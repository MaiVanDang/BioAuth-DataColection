# BioAuth Data Collection — DataCollectV2

Ứng dụng Android thu thập dữ liệu sinh trắc học hành vi (Behavioural Biometrics) phục vụ nghiên cứu khoa học tại Đại học Bách Khoa Hà Nội. Ứng dụng ghi lại dữ liệu cảm biến chuyển động (IMU) và nhịp điệu thao tác màn hình (touch, keystroke, scroll) trong khi người dùng thực hiện các hoạt động hàng ngày.

---

## Mục lục

- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt & Build](#cài-đặt--build)
- [Luồng sử dụng](#luồng-sử-dụng)
- [Cấu trúc dữ liệu đầu ra](#cấu-trúc-dữ-liệu-đầu-ra)
- [Schema CSV](#schema-csv)
- [Cấu trúc project](#cấu-trúc-project)
- [Kiến trúc & Luồng dữ liệu](#kiến-trúc--luồng-dữ-liệu)
- [Permissions](#permissions)
- [Các vấn đề đã biết & Fix](#các-vấn-đề-đã-biết--fix)

---

## Yêu cầu hệ thống

| Mục | Yêu cầu |
|---|---|
| Android SDK tối thiểu | API 24 (Android 7.0 Nougat) |
| Android SDK mục tiêu | API 36 |
| Kotlin | 2.0.21 |
| AGP | 8.9.1 |
| JVM target | Java 11 |
| Cảm biến bắt buộc | Accelerometer, Gyroscope |
| Cảm biến tùy chọn | Magnetometer (từ trường) |

---

## Cài đặt & Build

```bash
# Clone project
git clone <repo-url>
cd DataCollectV2

# Build debug APK
./gradlew assembleDebug

# Cài trực tiếp lên thiết bị
./gradlew installDebug
```

> Yêu cầu Android Studio Hedgehog (2023.1.1) trở lên hoặc JDK 11+.

---

## Luồng sử dụng

```
RegistrationActivity
        │  Nhập thông tin (tên, tuổi, giới tính, tay thuận, thiết bị)
        │  → Lưu metadata.csv
        ▼
SensorCollectionActivity  ◄──────────────────────────────────┐
        │  Thu cảm biến IMU cho 3 hoạt động:                  │
        │    • Đi bộ (mục tiêu: 210s)                         │
        │    • Đứng  (mục tiêu: 300s)                         │
        │    • Ngồi  (mục tiêu: 300s)                         │
        │  Mỗi lần nhấn Dừng → lưu {activity}_att{N}.csv      │
        ▼                                                      │
FormActivity                                                   │
        │  Điền form 3 phần (tối đa 2 lần/phiên):             │
        │    • Phần 1: 14 câu trắc nghiệm                     │
        │    • Phần 2: 7 câu nhập văn bản                     │
        │    • Phần 3: 5 câu chọn nhiều (checkbox)            │
        │  → Lưu tap_r{N}.csv, keystroke_r{N}.csv,            │
        │    scroll_r{N}.csv                                   │
        ▼
UploadActivity
        │  Xem thống kê dữ liệu đã thu                        │
        ├──[Thu thêm dữ liệu]──────────────────────────────────┘
        │    → Tăng session (session_1 → session_2, ...)
        │    → Quay lại SensorCollectionActivity
        │
        └──[Export ZIP & Gửi]
             → Nén toàn bộ userId/ thành ZIP
             → Chia sẻ qua ứng dụng khác (Gmail, Drive, ...)
             → Xóa dữ liệu gốc sau khi ZIP thành công
```

---

## Cấu trúc dữ liệu đầu ra

Sau khi giải nén file ZIP nhận được, cấu trúc thư mục sẽ như sau:

```
user_<timestamp>/
├── metadata.csv
├── session_1/
│   ├── walking_att1.csv
│   ├── walking_att2.csv      ← nếu thu nhiều lần
│   ├── standing_att1.csv
│   ├── sitting_att1.csv
│   ├── tap_r1.csv
│   ├── tap_r2.csv            ← nếu điền form lần 2
│   ├── keystroke_r1.csv
│   ├── keystroke_r2.csv
│   ├── scroll_r1.csv
│   └── scroll_r2.csv
├── session_2/
│   ├── walking_att1.csv
│   └── ...
└── session_N/
    └── ...
```

**Quy tắc đặt tên file:**
- `{activity}_att{N}.csv` — dữ liệu IMU, `N` tăng theo số lần thu trong cùng session
- `tap_r{N}.csv` — sự kiện chạm, `N` là lần điền form (1 hoặc 2)
- `keystroke_r{N}.csv` — sự kiện gõ phím
- `scroll_r{N}.csv` — sự kiện cuộn

---

## Schema CSV

### metadata.csv

Thông tin người tham gia, được tạo một lần khi đăng ký.

```
field,value
user_id,user_1777540974340
name,Nguyễn Văn A
age,22
gender,Nam
dominant_hand,Tay phải
device,Samsung SM-A546E (Android 14)
timestamp,1777540974340
```

---

### {activity}_att{N}.csv — Dữ liệu IMU

Thu ở tần số `SENSOR_DELAY_GAME` (~50–100 Hz tùy thiết bị).

| Cột | Kiểu | Mô tả |
|---|---|---|
| `timestamp_ms` | Long | Unix timestamp (ms) tính từ epoch UTC |
| `acc_x` | Float | Gia tốc trục X (m/s²) |
| `acc_y` | Float | Gia tốc trục Y (m/s²) |
| `acc_z` | Float | Gia tốc trục Z (m/s²) |
| `gyro_x` | Float | Tốc độ góc trục X (rad/s) |
| `gyro_y` | Float | Tốc độ góc trục Y (rad/s) |
| `gyro_z` | Float | Tốc độ góc trục Z (rad/s) |
| `mag_x` | Float | Từ trường trục X (µT), 0 nếu không có cảm biến |
| `mag_y` | Float | Từ trường trục Y (µT) |
| `mag_z` | Float | Từ trường trục Z (µT) |
| `activity` | String | Nhãn hoạt động: `walking`, `standing`, `sitting` |
| `session_id` | String | Ví dụ: `session_1`, `session_2` |

---

### tap_r{N}.csv — Sự kiện chạm

| Cột | Kiểu | Mô tả |
|---|---|---|
| `timestamp_ms` | Long | Unix timestamp (ms) |
| `x` | Float | Tọa độ X trên màn hình (px) |
| `y` | Float | Tọa độ Y trên màn hình (px) |
| `pressure` | Float | Áp lực chạm (0.0–1.0) |
| `size` | Float | Diện tích tiếp xúc |
| `element_id` | String | ID thành phần được chạm (vd: `mc_q1_choice_A`) |
| `phase` | String | `DOWN` hoặc `UP` |
| `hold_ms` | Long | Thời gian giữ ngón tay (ms), chỉ có ở `UP` |
| `session_id` | String | Session hiện tại |

---

### keystroke_r{N}.csv — Sự kiện gõ phím

| Cột | Kiểu | Mô tả |
|---|---|---|
| `timestamp_ms` | Long | Unix timestamp (ms) |
| `field_id` | String | ID ô nhập (vd: `text_q1`, `text_q2`) |
| `char_count` | Int | Số ký tự hiện tại trong ô |
| `inter_key_ms` | Long | Khoảng thời gian kể từ lần gõ trước (ms) |
| `is_delete` | Boolean | `true` nếu là thao tác xóa |
| `session_id` | String | Session hiện tại |

---

### scroll_r{N}.csv — Sự kiện cuộn

| Cột | Kiểu | Mô tả |
|---|---|---|
| `timestamp_ms` | Long | Unix timestamp (ms) |
| `x` | Float | Tọa độ X (px) |
| `y` | Float | Tọa độ Y (px) |
| `pressure` | Float | Áp lực (0.0–1.0) |
| `size` | Float | Diện tích tiếp xúc |
| `phase` | String | `DOWN`, `MOVE`, hoặc `UP` |
| `pointer_id` | Int | ID ngón tay (hỗ trợ đa điểm chạm) |
| `session_id` | String | Session hiện tại |

---

## Cấu trúc project

```
app/src/main/java/com/datn/datacollectv2/
├── MainActivity.kt                  # Entry point, redirect logic
├── RegistrationActivity.kt          # Đăng ký người dùng mới
├── SensorCollectionActivity.kt      # Màn hình thu cảm biến IMU chính
├── SensorForegroundService.kt       # Foreground service giữ timer khi app background
├── FormActivity.kt                  # Màn hình điền form thu thập touch/keystroke/scroll
├── UploadActivity.kt                # Thống kê & xuất ZIP
├── RecordingSession.kt              # Data class trạng thái một lần thu
├── UserSession.kt                   # Quản lý đăng nhập / SharedPreferences user
├── PermissionHelper.kt              # Xin quyền runtime
├── data/
│   ├── TapEvent.kt                  # Model sự kiện chạm
│   ├── KeystrokeEvent.kt            # Model sự kiện gõ phím
│   └── ScrollEvent.kt               # Model sự kiện cuộn
└── view/
    └── SensorBarChartView.kt        # Custom view biểu đồ realtime
```

---

## Kiến trúc & Luồng dữ liệu

### SharedPreferences được dùng

| Tên prefs | Key | Mô tả |
|---|---|---|
| `user_session_v1` | `logged_in`, `user_id`, `name`, ... | Thông tin đăng nhập người dùng |
| `session_prefs` | `current_session_id` | Session đang active, vd: `session_1` |
| `sensor_state_<userId>` | `target_<activity>`, `accMs_<activity>`, `attempt_<activity>` | Trạng thái tiến độ thu cảm biến |
| `form_draft` | `session_id`, `round`, `mc_<N>`, `text_<N>`, ... | Draft form chưa submit |

### Vòng đời session

```
Đăng ký → session_prefs["current_session_id"] = "session_1"
Thu xong phiên 1 → vào UploadActivity
  ├── [Export ZIP] → nén userId/ → xóa session_N/ → reset sensor_state
  └── [Thu thêm]  → incrementSessionNumber() → session_1 → session_2
                   → reset sensor_state → quay lại SensorCollectionActivity
```

### SensorForegroundService

Service dạng `START_STICKY` chạy liên tục để giữ timer chính xác kể cả khi người dùng rời app. `onTick` callback được Activity attach lại mỗi khi `onStart()` và `onNewIntent()` được gọi để tránh memory leak.

---

## Permissions

| Permission | Mục đích |
|---|---|
| `FOREGROUND_SERVICE` | Chạy service thu cảm biến |
| `FOREGROUND_SERVICE_DATA_SYNC` | Loại foreground service trên Android 14+ |
| `POST_NOTIFICATIONS` | Hiển thị notification trạng thái thu |
| `ACTIVITY_RECOGNITION` | Nhận diện hoạt động thể chất |
| `BODY_SENSORS` | Đọc dữ liệu cảm biến |
| `WAKE_LOCK` | Giữ CPU khi màn hình tắt |
| `WRITE_EXTERNAL_STORAGE` | Lưu file CSV (Android ≤ 9) |
| `READ_EXTERNAL_STORAGE` | Đọc file (Android ≤ 12) |

---

