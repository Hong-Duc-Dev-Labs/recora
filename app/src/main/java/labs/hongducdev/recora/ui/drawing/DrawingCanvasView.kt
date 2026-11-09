package labs.hongducdev.recora.ui.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import labs.hongducdev.recora.model.DrawingStroke
import java.util.Stack
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class DrawingCanvasView(
    context: Context,
) : View(context) {
    var topBarRect: Rect? = null

    private val strokes = Stack<DrawingStroke>()
    private var currentPath = Path()
    private var currentColor = Color.RED
    private var currentStrokeWidth = 14f

    private var previousX = 0f
    private var previousY = 0f

    private val paint =
        Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    fun setColor(color: Int) {
        currentColor = color
    }

    fun setStrokeWidth(width: Float) {
        currentStrokeWidth = width
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.pop()
            invalidate()
        }
    }

    fun clear() {
        strokes.clear()
        currentPath.reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.strokeWidth
            canvas.drawPath(stroke.path, paint)
        }

        // Draw current active stroke
        paint.color = currentColor
        paint.strokeWidth = currentStrokeWidth
        canvas.drawPath(currentPath, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        topBarRect?.let { rect ->
            if (event.y <= rect.bottom + 30 && event.y >= rect.top - 30 &&
                event.x >= rect.left - 30 && event.x <= rect.right + 30
            ) {
                return false
            }
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                previousX = x
                previousY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - previousX)
                val dy = abs(y - previousY)
                if (dx >= 4 || dy >= 4) {
                    currentPath.quadTo(previousX, previousY, (x + previousX) / 2, (y + previousY) / 2)
                    previousX = x
                    previousY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(previousX, previousY)
                strokes.push(DrawingStroke(Path(currentPath), currentColor, currentStrokeWidth))
                currentPath.reset()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
