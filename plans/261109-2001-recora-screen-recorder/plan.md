# Plan: Recora - Android Screen Recorder with MD3 Expressive UI & Floating Pill Controller

- **Project**: Recora
- **Package**: `labs.hongducdev.recora`
- **Stack**: Android Jetpack Compose, Material 3 Expressive (`md3-design`), Kotlin, MediaProjection API
- **Target OS**: Android 10 (API 29) - Android 15 (API 35)
- **Status**: Proposed (Awaiting Approval)

---

## 1. Tổng quan & Yêu cầu từ hình ảnh tham chiếu

Dựa trên hình ảnh bạn gửi (`image.png`), Recora sẽ có thanh điều khiển nổi trên màn hình (**Floating Pill Toolbar**) được thiết kế bám sát 100% ngôn ngữ **Material 3 Expressive (`sameerasw/essentials`)**:

### Chi tiết thanh Floating Control Bar nổi:
1. **Hình dạng Capsule / Pill**: Bo tròn hoàn toàn hai đầu, nền dynamic/surface tint với độ trong suốt tinh tế.
2. **Kéo thả & Bám cạnh (Draggable & Snap to Edge)**: Người dùng có thể kéo thả tự do tới bất kỳ vị trí nào trên màn hình, khi nhả tay sẽ tự động hít nhẹ vào mép màn hình trái hoặc phải kèm rung haptic.
3. **Các thành phần điều khiển (từ trái qua phải theo ảnh)**:
   - **Nút Thu gọn / Mở rộng (`>`)**: Thu gọn thanh toolbar thành một chấm/bong bóng nhỏ áp sát viền màn hình để không che khuất nội dung, bấm lại để bung ra đầy đủ.
   - **Bút vẽ màn hình (`✏ Pencil`)**: Kích hoạt chế độ vẽ ghi chú trực tiếp lên màn hình thời gian thực khi đang quay.
   - **Bật/Tắt Facecam (`👤 Camera/Person`)**: Mở khung tròn camera trước (PIP) nổi trên màn hình để thu hình khuôn mặt.
   - **Inner Pill (Cụm điều khiển chính có nền tương phản)**:
     - **Nút Tạm dừng / Tiếp tục (`||` / `▶`)**: Tạm dừng ghi hình mà không làm ngắt file video.
     - **Nút Dừng (`■ Stop`)**: Kết thúc ghi hình, lưu file MP4 vào bộ nhớ và hiện thông báo hoàn tất.
   - **Hiển thị thời gian quay (`00:01`)**: Timer đếm giây sắc nét bằng font Google Sans Flex, đồng bộ chính xác với luồng video.

---

## 2. Kiến trúc Hệ thống (System Architecture)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Recora Application                              │
├────────────────────────────────┬───────────────────────────────────────┤
│          UI Layer              │             Core Service Layer        │
│  (Jetpack Compose + MD3 Kit)   │                                       │
│                                │                                       │
│  1. MainScreen (AppShell)      │  1. ScreenRecordingService            │
│     - PullTitleHeader          │     - Foreground Service (Projection) │
│     - HorizontalPager 3 Tabs:  │     - MediaProjection + VirtualDisplay│
│       * Tab 1: Record / Hero   │     - MediaRecorder (Video + Audio)   │
│       * Tab 2: Gallery Video   │     - MediaStore Scoped Storage       │
│       * Tab 3: Settings M3     │                                       │
│     - Floating Toolbar Pill    │  2. FloatingControlService            │
│                                │     - WindowManager System Overlay    │
│  2. Floating Controller Overlay│     - ComposeView Pill Toolbar        │
│     - Expand / Collapse        │     - Touch Drag & Snap to Edge       │
│     - Timer display            │                                       │
│     - Pause/Resume/Stop        │  3. DrawingOverlayService             │
│                                │     - Fullscreen Transparent Overlay  │
│  3. Drawing Canvas Overlay     │     - Smooth Path Drawing             │
│     - Palette, Stroke, Clear   │     - Color Picker, Undo, Clear       │
│                                │                                       │
│  4. Facecam PIP Overlay        │  4. FacecamService                    │
│     - Draggable Circle Camera  │     - CameraX Front Camera Preview    │
└────────────────────────────────┴───────────────────────────────────────┘
```

---

## 3. Lộ trình Triển khai (Phases)

### Phase 1: Khởi tạo Project & Cài đặt `md3-design` Kit
- Khởi tạo cấu trúc dự án Android Gradle với Kotlin DSL (`build.gradle.kts`) và Version Catalog (`libs.versions.toml`).
- Package: `labs.hongducdev.recora`.
- Cài đặt bộ kit `md3-design` từ skill (`install_essentials_ui.py`):
  - Theme Pitch-black OLED (`#000000`).
  - Font Google Sans Flex (`ROND=100`).
  - Haptic feedback utilities (`HapticUtil.kt`).
  - Các components: `PullTitleHeader`, `EssentialsFloatingToolbar`, `RoundedCardContainer`, `FeatureCard`, `ExpressiveSlider`, `SegmentedPicker`.
- Cấu hình Manifest với đầy đủ quyền: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `CAMERA`.

### Phase 2: Xây dựng Core Screen Recording Engine
- `ScreenRecordingService`: Foreground Service chuẩn Android 14/15 (`foregroundServiceType="mediaProjection"`).
- Quản lý `MediaProjectionManager`, yêu cầu cấp quyền từ Activity.
- Khởi tạo `VirtualDisplay` và `MediaRecorder`:
  - Độ phân giải tự động thích ứng với màn hình thiết bị hoặc theo cài đặt (1080p, 720p, 2K).
  - Tốc độ khung hình (30fps / 60fps) và Bitrate (8Mbps - 20Mbps).
  - Nguồn âm thanh: Mic, Âm thanh hệ thống (Internal Audio qua `AudioPlaybackCaptureConfiguration`), hoặc Không tiếng.
- Lưu trữ video qua `MediaStore` API chuẩn Scoped Storage (vào thư mục `Movies/Recora`).
- Phát notification điều khiển có nút Pause, Resume, Stop.

### Phase 3: Xây dựng Floating Pill Toolbar (Nút nổi như ảnh)
- `FloatingControlService`: Quản lý hiển thị View nổi qua `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
- Sử dụng `ComposeView` để render trực tiếp UI Material 3 Expressive:
  - Khối pill capsule bo tròn, hiệu ứng mờ kính nhẹ (translucent glassmorphism / tonal surface).
  - Nút thu gọn `>` để gập vào cạnh mép màn hình thành badge nhỏ gọn.
  - Đồng hồ đếm thời gian quay thực (`00:00`).
  - Nút Pause/Resume (`||` / `▶`) và Stop (`■`).
  - Nút mở công cụ vẽ ✏ và nút mở Facecam 👤.
- Xử lý cử chỉ vuốt kéo (Drag & Drop) mượt mà với hiệu ứng hít vào mép màn hình (Snap to Edge) và rung haptic khi chạm cạnh.

### Phase 4: Xây dựng Công cụ Bút vẽ trên màn hình (Screen Drawing Tool)
- `DrawingOverlayService`: Lớp phủ màn hình trong suốt (Transparent Fullscreen Overlay) được kích hoạt khi bấm icon ✏.
- Canvas vẽ tay mượt mà (Smooth Cubic Bezier Curve) bắt tọa độ touch.
- Thanh công cụ vẽ tiện ích thu gọn:
  - Bảng màu vẽ (Đỏ, Vàng, Xanh lá, Xanh dương, Trắng,... theo bảng màu M3).
  - Điều chỉnh nét vẽ (thanh mảnh / bút dạ quang highlight).
  - Nút Hoàn tác (Undo).
  - Nút Xoá sạch (Clear all).
  - Nút Thoát chế độ vẽ để tiếp tục chạm vào các app bên dưới.

### Phase 5: Xây dựng Facecam PIP & Hoàn thiện UI App chính
- **Facecam**:
  - Tích hợp CameraX hiển thị luồng camera trước trong khung tròn nhỏ nổi trên màn hình.
  - Có thể kéo thả di chuyển vị trí khung camera, thay đổi kích thước hoặc tắt nhanh.
- **Màn hình chính App (Main Activity)**:
  - **Tab 1 - Ghi hình**: Hero Banner lớn với nút Record tròn to, thẻ trạng thái dung lượng trống, các toggle nhanh (Mic, Floating Toolbar, Facecam).
  - **Tab 2 - Thư viện video**: Danh sách video đã quay với thumbnail, thời lượng, dung lượng, cho phép phát video, chia sẻ và xoá.
  - **Tab 3 - Cài đặt**: Cài đặt thông số video (độ phân giải, fps, bitrate), nguồn thu âm, thời gian đếm ngược (Off, 3s, 5s), giao diện Pitch Black và rung haptic.

### Phase 6: Kiểm thử & Nghiệm thu
- Kiểm thử luồng cấp quyền (MediaProjection, Overlay, Notification, Mic, Camera).
- Kiểm tra tính ổn định khi quay: không drop frame, âm thanh đồng bộ, pause/resume trơn tru.
- Thử nghiệm trên màn hình thực tế, kiểm tra giao diện Floating Pill và nét vẽ không bị lệch toạ độ.

---

## 4. Tiêu chí nghiệm thu (Acceptance Criteria)
1. Ứng dụng build thành công APK/AAB sạch không cảnh báo nghiêm trọng.
2. Nút nổi trên màn hình khớp 100% về hình dạng, các nút chức năng (thu gọn, bút vẽ, facecam, pause, stop, timer) như ảnh bạn gửi.
3. Chế độ bút vẽ hoạt động mượt mà, cho phép vẽ tự do lên màn hình khi đang quay và nét vẽ được thu trọn vẹn vào video xuất ra.
4. Giao diện toàn app đồng nhất chuẩn `md3-design` (Pitch Black, font Google Sans Flex, haptic feedback).
