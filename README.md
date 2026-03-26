Nap2S ![Supported server version](https://img.shields.io/badge/minecraft-1.21+-brightgreen) ![Bedrock](https://img.shields.io/badge/bedrock-native%20forms-orange)
===========

Plugin nạp thẻ cào & chuyển khoản ngân hàng cho server Minecraft. Hỗ trợ nhiều cổng gạch thẻ, QR code thanh toán in-game, giao diện native cho Bedrock/PE, và hệ thống quản trị doanh thu toàn diện.

**Cổng nạp thẻ:** ChargingWS-v2, Card2K, Trumcao, TSRPay, NhapThe, GameBank

**Cổng chuyển khoản:** [SePay](https://sepay.vn/) (VietQR), [PayOS](https://payos.vn/) (Payment Link)

Tính năng
===========

**Nạp thẻ cào**
- Nạp thẻ tự động qua 6 cổng, routing thông minh theo loại thẻ
- Tự chuyển cổng dự phòng khi cổng chính lỗi
- Bonus khuyến mãi riêng theo loại thẻ, theo cổng
- Lịch khuyến mãi hẹn giờ: tự bật/tắt, hiển thị trên GUI
- Config giao diện linh hoạt, thay đổi vị trí icon tùy ý

**Nạp chuyển khoản (ATM/QR)**
- Tạo QR VietQR tức thì — hỗ trợ tất cả ngân hàng VN
- QR code hiển thị full-screen trên bản đồ in-game — quét ngay bằng điện thoại
- Trang thanh toán web tích hợp cho mobile
- Webhook nhận thanh toán tự động, cộng xu real-time
- Countdown hết hạn trên ActionBar
- Nhập số tiền tùy chọn (bội 1.000) hoặc chọn nhanh mệnh giá preset
- Lưu snapshot thông tin ngân hàng theo đơn — admin đổi TK không ảnh hưởng đơn đang chờ

**Bedrock / PE**
- Giao diện native form — không dùng chest GUI giả lập
- Form nạp thẻ: dropdown loại thẻ, mệnh giá + text input seri/mã
- Form chuyển khoản: chọn nhanh hoặc nhập số tiền
- Lịch sử giao dịch: danh sách → chạm xem chi tiết
- Hiển thị khuyến mãi trực tiếp trên form (VD: `Viettel +20%`)
- Tự phát hiện Bedrock qua Floodgate, fallback chest GUI nếu không có

**Quản trị & Bảo mật**
- API key mã hóa lưu trong database — không lộ qua file config
- Rate limit nạp thẻ/ATM theo ngày (số lần + tổng tiền)
- Audit log ghi mọi thao tác admin
- Báo cáo doanh thu theo ngày/tuần/tháng/tùy chỉnh
- Config validator — khởi động là biết thiếu gì

**Mốc nạp & Top nạp**
- Top nạp toàn thời gian / tuần / tháng
- Mốc nạp cá nhân + mốc nạp tổng server
- BossBar hiện tiến độ real-time
- Network mode: top nạp & mốc nạp cross-server qua MySQL chung

Hướng dẫn sử dụng
===========

**Yêu cầu:**

- [Paper](https://papermc.io/) 1.21+ (hoặc Folia)
- [MineVNLib](https://github.com/minevn) — thư viện core
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) — quản lý xu
- (Tùy chọn) [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — placeholder top nạp
- (Tùy chọn) [Floodgate](https://geysermc.org/) — native form cho Bedrock

**Cài đặt:**

1. Bỏ `Nap2S.jar` và `MineVNLib.jar` vào `plugins/`
2. Khởi động server — plugin tạo file config mẫu
3. Chạy `/nap2s setup` trong game — wizard hướng dẫn từng bước
4. Sửa config theo hướng dẫn, rồi `/nap2s reload`

**Danh sách lệnh:**

| Lệnh | Chức năng | Permission |
|---|---|---|
| `/napthe` | Mở giao diện nạp thẻ | Người chơi |
| `/napthe <loại> <giá> <seri> <mã>` | Nạp thẻ nhanh (auto-complete) | Người chơi |
| `/napthe lichsu` | Xem lịch sử nạp thẻ | Người chơi |
| `/napatm` | Mở giao diện chuyển khoản | Người chơi |
| `/napatm lichsu` | Xem lịch sử chuyển khoản | Người chơi |
| `/napatm status` | Kiểm tra đơn đang chờ | Người chơi |
| `/napatm cancel` | Hủy đơn đang chờ | Người chơi |
| `/topnap` | Xem bảng xếp hạng nạp | nap2s.topnap |
| `/nap2s setup` | Wizard cài đặt lần đầu | nap2s.admin |
| `/nap2s reload` | Tải lại config | nap2s.admin |
| `/nap2s report [today\|week\|month]` | Báo cáo doanh thu | nap2s.admin |
| `/nap2s setkey <key> <value>` | Lưu API key mã hóa vào DB | nap2s.admin |
| `/nap2s napthucong <player> <xu>` | Cộng xu thủ công | nap2s.admin |
| `/nap2s tracuugd <mã>` | Tra cứu giao dịch | nap2s.admin |
| `/nap2s lichsu` | Xem lịch sử nạp | nap2s.admin |
| `/nap2s auditlog` | Xem log thao tác admin | nap2s.admin |

**Placeholder:**

Sử dụng với PlaceholderAPI để hiển thị top nạp trên scoreboard, hologram, tab...

| Placeholder | Chức năng | Ghi chú |
|---|---|---|
| `%NAP2S_TOP_DONATE_TOTAL_X_PLAYER%` | Tên người chơi đứng top X | Thay `TOTAL` bằng `WEEK` hoặc `MONTH` để lấy top tuần/tháng |
| `%NAP2S_TOP_DONATE_TOTAL_X_VALUE%` | Số tiền nạp của top X | X là thứ hạng (1, 2, 3...) |
| `%NAP2S_DATA_DONATE_TOTAL%` | Tổng tiền đã nạp của người chơi | Thay `TOTAL` bằng `WEEK` hoặc `MONTH` cho tuần/tháng |

> Ví dụ: `%NAP2S_TOP_DONATE_TOTAL_1_PLAYER%` trả về tên người đứng top 1 nạp thẻ

**Config plugin:**

Cấu trúc thư mục `./plugins/Nap2S` như sau:
```
Nap2S/
├── menu/
│   └── napthe/
│       ├── loaithe.yml          # Giao diện chọn loại thẻ
│       └── menhgia.yml          # Giao diện chọn mệnh giá
├── providers/
│   ├── chargingws.yml           # Cổng nạp thẻ (thêm API key tại đây)
│   └── card2k.yml
├── config.yml                   # Cài đặt chung
├── banking.yml                  # Cấu hình nạp ATM/QR
├── messages.yml                 # Tin nhắn tùy chỉnh
└── mocnap.yml                   # Mốc nạp tích lũy
```

- Giao diện chest GUI config tại `loaithe.yml` và `menhgia.yml`
- API key cổng nạp thẻ đặt tại file tương ứng trong `providers/`, hoặc dùng lệnh `/nap2s setkey` để lưu mã hóa vào database
- Cài đặt chung tại `config.yml`, tin nhắn tại `messages.yml`
- Cài đặt nạp ATM/QR tại `banking.yml` (SePay hoặc PayOS)
- Mốc nạp tích lũy và thưởng tại `mocnap.yml`

Webhook
===========

Plugin tích hợp HTTP server nhận webhook tự động:

| Cổng | URL Webhook |
|---|---|
| SePay | `https://your-domain.com/nap2s/ipn` |
| PayOS | `https://your-domain.com/nap2s/ipn/payos` |

Cần mở port `webserver-port` (mặc định 3510) hoặc dùng reverse proxy (nginx, ngrok).

Hình ảnh
===========

> *Thêm ảnh chụp màn hình tại đây*

---

Plugin được phát triển dựa trên nền tảng [DotMan](https://github.com/minevn/dotman), mở rộng thêm nạp chuyển khoản, giao diện Bedrock native, bảo mật API key, và hệ thống quản trị doanh thu. Mọi đóng góp và phản hồi đều được đón nhận.
