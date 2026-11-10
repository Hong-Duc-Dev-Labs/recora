package labs.hongducdev.recora.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Bundle
import android.util.Log
import android.view.Surface
import labs.hongducdev.recora.model.RecordingConfig
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Screen recorder for sessions that capture the device's internal audio, which `MediaRecorder` cannot do.
 * Video comes from a codec input surface, audio is the playback capture, optionally mixed with the
 * microphone, and both are muxed into one MP4. See docs/system-architecture.md for why this path exists.
 */
class CodecScreenRecorder(
    private val config: RecordingConfig,
    private val projection: MediaProjection,
    private val output: FileDescriptor,
) {
    private class PendingSample(
        val isVideo: Boolean,
        val data: ByteBuffer,
        val info: MediaCodec.BufferInfo,
    )

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val pending = ArrayList<PendingSample>()
    private val muxLock = Any()
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L

    @Volatile private var running = false

    @Volatile private var stopped = false

    /** Start of the current pause, or -1 while recording. Samples at or after it are dropped. */
    @Volatile private var pauseStartUs = -1L

    @Volatile private var pausedTotalUs = 0L
    private var startUs = 0L

    lateinit var inputSurface: Surface
        private set

    @SuppressLint("MissingPermission")
    fun prepare() {
        muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.resolutionWidth, config.resolutionHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // A static screen sends no frames, which would delay the first output format and the muxer start.
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 200_000L)
            }
        videoEncoder =
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                it.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = it.createInputSurface()
            }

        val audioFormat =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_FRAMES * BYTES_PER_STEREO_FRAME * 4)
            }
        audioEncoder =
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                it.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

        internalRecord = buildInternalRecord()
        if (config.audioSource.hasMic) micRecord = buildMicRecord()
    }

    @SuppressLint("MissingPermission")
    private fun buildInternalRecord(): AudioRecord {
        val captureConfig =
            AudioPlaybackCaptureConfiguration
                .Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        val format =
            AudioFormat
                .Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val record =
            AudioRecord
                .Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer, CHUNK_FRAMES * BYTES_PER_STEREO_FRAME * 4))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Internal audio capture could not be initialized" }
        return record
    }

    @SuppressLint("MissingPermission")
    private fun buildMicRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_FRAMES * 2 * 4),
            )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
        return record
    }

    fun start() {
        startUs = nowUs()
        running = true
        videoEncoder?.start()
        audioEncoder?.start()
        internalRecord?.startRecording()
        micRecord?.startRecording()

        videoThread = Thread({ runCatching { videoLoop() }.onFailure { Log.e(TAG, "video loop failed", it) } }, "recora-video").also { it.start() }
        audioThread = Thread({ runCatching { audioLoop() }.onFailure { Log.e(TAG, "audio loop failed", it) } }, "recora-audio").also { it.start() }
    }

    fun pause() {
        if (pauseStartUs < 0) pauseStartUs = nowUs()
    }

    fun resume() {
        val pausedAt = pauseStartUs
        if (pausedAt < 0) return
        pausedTotalUs += nowUs() - pausedAt
        pauseStartUs = -1L
        // Frames dropped during the pause may be references for the next ones, so restart from a key frame.
        runCatching {
            videoEncoder?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    /** Finishes the file and frees every codec and recorder. Safe to call more than once. */
    fun stop() {
        if (stopped) return
        stopped = true
        running = false

        audioThread?.join(JOIN_TIMEOUT_MS)
        runCatching { videoEncoder?.signalEndOfInputStream() }
        videoThread?.join(JOIN_TIMEOUT_MS)

        synchronized(muxLock) {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            pending.clear()
        }

        runCatching { internalRecord?.stop() }
        runCatching { internalRecord?.release() }
        runCatching { micRecord?.stop() }
        runCatching { micRecord?.release() }
        runCatching { audioEncoder?.stop() }
        runCatching { audioEncoder?.release() }
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        runCatching { inputSurface.release() }
    }

    private fun videoLoop() {
        val encoder = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onTrackFormat(isVideo = true, encoder.outputFormat)

                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && buffer != null) writeSample(isVideo = true, buffer, info)
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun audioLoop() {
        val encoder = audioEncoder ?: return
        val internal = internalRecord ?: return
        val mic = micRecord
        val info = MediaCodec.BufferInfo()

        val systemSamples = ShortArray(CHUNK_FRAMES * 2)
        val micSamples = ShortArray(CHUNK_FRAMES)
        val pcm = ByteArray(CHUNK_FRAMES * BYTES_PER_STEREO_FRAME)

        var baseUs = 0L
        var framesSinceBase = 0L
        var wasPaused = true

        while (running) {
            val systemRead = internal.read(systemSamples, 0, systemSamples.size, AudioRecord.READ_BLOCKING)
            if (systemRead < 0) break
            if (systemRead == 0) continue
            val frames = systemRead / 2
            val micRead = mic?.read(micSamples, 0, frames, AudioRecord.READ_BLOCKING)?.coerceAtLeast(0) ?: 0

            if (pauseStartUs >= 0) {
                // Keep reading so the capture buffers do not overflow, but write nothing.
                wasPaused = true
                continue
            }
            if (wasPaused) {
                baseUs = nowUs() - frames * 1_000_000L / SAMPLE_RATE
                framesSinceBase = 0
                wasPaused = false
            }

            for (frame in 0 until frames) {
                val micValue = if (frame < micRead) micSamples[frame].toInt() else 0
                for (channel in 0..1) {
                    val mixed = (systemSamples[frame * 2 + channel] + micValue).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    val at = (frame * 2 + channel) * 2
                    pcm[at] = (mixed and 0xFF).toByte()
                    pcm[at + 1] = (mixed shr 8).toByte()
                }
            }

            val ptsUs = baseUs + framesSinceBase * 1_000_000L / SAMPLE_RATE
            queueAudio(encoder, info, pcm, frames * BYTES_PER_STEREO_FRAME, ptsUs)
            framesSinceBase += frames
        }

        finishAudio(encoder, info, baseUs + framesSinceBase * 1_000_000L / SAMPLE_RATE)
    }

    private fun queueAudio(
        encoder: MediaCodec,
        info: MediaCodec.BufferInfo,
        data: ByteArray,
        size: Int,
        ptsUs: Long,
    ) {
        var offset = 0
        while (offset < size) {
            val index = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                drainAudio(encoder, info, 0)
                continue
            }
            val buffer = encoder.getInputBuffer(index) ?: return
            buffer.clear()
            val count = minOf(buffer.remaining(), size - offset)
            buffer.put(data, offset, count)
            val chunkPts = ptsUs + (offset / BYTES_PER_STEREO_FRAME) * 1_000_000L / SAMPLE_RATE
            encoder.queueInputBuffer(index, 0, count, chunkPts, 0)
            offset += count
            drainAudio(encoder, info, 0)
        }
    }

    private fun finishAudio(
        encoder: MediaCodec,
        info: MediaCodec.BufferInfo,
        ptsUs: Long,
    ) {
        var queued = false
        repeat(FINISH_ATTEMPTS) {
            if (!queued) {
                val index = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (index >= 0) {
                    encoder.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queued = true
                }
            }
        }
        if (!queued) return
        repeat(FINISH_ATTEMPTS) {
            if (drainAudio(encoder, info, DEQUEUE_TIMEOUT_US)) return
        }
    }

    /** Returns true once the end-of-stream buffer has been seen. */
    private fun drainAudio(
        encoder: MediaCodec,
        info: MediaCodec.BufferInfo,
        timeoutUs: Long,
    ): Boolean {
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onTrackFormat(isVideo = false, encoder.outputFormat)

                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && buffer != null) writeSample(isVideo = false, buffer, info)
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }

                else -> return false
            }
        }
    }

    private fun onTrackFormat(
        isVideo: Boolean,
        format: MediaFormat,
    ) = synchronized(muxLock) {
        val target = muxer ?: return@synchronized
        if (isVideo) videoTrack = target.addTrack(format) else audioTrack = target.addTrack(format)
        if (videoTrack >= 0 && audioTrack >= 0 && !muxerStarted) {
            target.start()
            muxerStarted = true
            pending.forEach { write(it.isVideo, it.data, it.info) }
            pending.clear()
        }
    }

    private fun writeSample(
        isVideo: Boolean,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) = synchronized(muxLock) {
        val pausedAt = pauseStartUs
        if (pausedAt >= 0 && info.presentationTimeUs >= pausedAt) return@synchronized

        val last = if (isVideo) lastVideoPtsUs else lastAudioPtsUs
        var ptsUs = (info.presentationTimeUs - startUs - pausedTotalUs).coerceAtLeast(0)
        if (ptsUs <= last) ptsUs = last + 1
        if (isVideo) lastVideoPtsUs = ptsUs else lastAudioPtsUs = ptsUs

        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val sampleInfo = MediaCodec.BufferInfo().apply { set(0, info.size, ptsUs, info.flags) }

        if (muxerStarted) {
            write(isVideo, buffer, sampleInfo)
        } else {
            val copy = ByteBuffer.allocate(info.size).put(buffer).also { it.flip() }
            pending.add(PendingSample(isVideo, copy, sampleInfo))
        }
    }

    private fun write(
        isVideo: Boolean,
        data: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        val track = if (isVideo) videoTrack else audioTrack
        runCatching { muxer?.writeSampleData(track, data, info) }.onFailure { Log.e(TAG, "mux write failed", it) }
    }

    private fun nowUs() = System.nanoTime() / 1000

    private companion object {
        const val TAG = "CodecScreenRecorder"
        const val SAMPLE_RATE = 44100
        const val AUDIO_BIT_RATE = 128_000
        const val CHUNK_FRAMES = 1024
        const val BYTES_PER_STEREO_FRAME = 4
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val FINISH_ATTEMPTS = 100
        const val JOIN_TIMEOUT_MS = 3000L
    }
}
