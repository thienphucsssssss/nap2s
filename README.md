Nap2S ![Supported server version](https://img.shields.io/badge/minecraft-1.21+-brightgreen) ![Bedrock](https://img.shields.io/badge/bedrock-hỗ%20trợ-orange)
===========

Plugin nạp thẻ cào và chuyển khoản ngân hàng cho server Minecraft. Hỗ trợ nhiều cổng gạch thẻ, quét QR ngay trong game, giao diện riêng cho người chơi Bedrock/PE, và công cụ quản lý doanh thu cho admin.

**Cổng nạp thẻ:** ChargingWS-v2, Card2K, Thesieure, Gachthe1s,...

**Cổng chuyển khoản:** [SePay](https://sepay.vn/) (VietQR), [PayOS](https://payos.vn/)

Tính năng
===========

**Nạp thẻ cào**
- Hỗ trợ nhiều cổng gạch thẻ, tự chuyển cổng dự phòng khi cổng chính lỗi
- Khuyến mãi riêng theo loại thẻ, theo cổng
- Hẹn giờ khuyến mãi: tự bật/tắt, hiển thị trên giao diện
- Giao diện linh hoạt, thay đổi vị trí icon tùy ý

**Nạp chuyển khoản**
- Tạo mã QR tức thì — hỗ trợ tất cả ngân hàng Việt Nam
- Mã QR hiển thị trên bản đồ trong game — quét ngay bằng điện thoại
- Có trang thanh toán riêng mở trên trình duyệt
- Tự nhận biết khi người chơi đã chuyển tiền, cộng xu ngay lập tức
- Đếm ngược thời gian hết hạn đơn
- Nhập số tiền tùy chọn hoặc chọn nhanh mệnh giá có sẵn

**Bedrock / PE**
- Giao diện riêng cho người chơi Bedrock — không dùng giao diện rương
- Form nạp thẻ: chọn loại thẻ, mệnh giá, nhập seri và mã thẻ
- Form chuyển khoản: chọn nhanh hoặc nhập số tiền
- Xem lịch sử giao dịch: danh sách → chạm để xem chi tiết
- Hiển thị khuyến mãi ngay trên form (VD: `Viettel +20%`)
- Tự nhận biết người chơi Bedrock, không cần cài thêm gì

**Quản trị**
- API key được mã hóa, lưu trong database — không lộ qua file config
- Giới hạn số lần nạp và tổng tiền mỗi ngày (chống lạm dụng)
- Ghi lại mọi thao tác admin
- Báo cáo doanh thu theo ngày, tuần, tháng
- Kiểm tra cấu hình khi khởi động — báo thiếu gì ngay trong console

**Mốc nạp & Top nạp**
- Top nạp toàn thời gian, theo tuần, theo tháng
- Mốc nạp cá nhân + mốc nạp cả server cùng góp
- Thanh tiến độ hiển thị trên BossBar
- Chạy nhiều server dùng chung database: top nạp và mốc nạp tự đồng bộ

Hướng dẫn sử dụng
===========

**Cần có:**

- [Paper](https://papermc.io/) 1.21+ (hoặc Folia)
- [MineVNLib](https://github.com/minevn) — thư viện chung
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) — quản lý xu
- (Tùy chọn) [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — hiển thị top nạp
- (Tùy chọn) [Floodgate](https://geysermc.org/) — giao diện riêng cho Bedrock

**Cài đặt:**

1. Bỏ `Nap2S.jar` và `MineVNLib.jar` vào `plugins/`
2. Khởi động server — plugin tự tạo file cấu hình mẫu
3. Chạy `/nap2s setup` trong game — có hướng dẫn từng bước
4. Sửa cấu hình theo hướng dẫn, rồi `/nap2s reload`

**Danh sách lệnh:**

| Lệnh | Chức năng | Quyền |
|---|---|---|
| `/napthe` | Mở giao diện nạp thẻ | Người chơi |
| `/napthe <loại> <giá> <seri> <mã>` | Nạp thẻ nhanh | Người chơi |
| `/napthe lichsu` | Xem lịch sử nạp thẻ | Người chơi |
| `/napatm` | Mở giao diện chuyển khoản | Người chơi |
| `/napatm lichsu` | Xem lịch sử chuyển khoản | Người chơi |
| `/napatm status` | Kiểm tra đơn đang chờ | Người chơi |
| `/napatm cancel` | Hủy đơn đang chờ | Người chơi |
| `/topnap` | Xem bảng xếp hạng nạp | nap2s.topnap |
| `/nap2s setup` | Hướng dẫn cài đặt lần đầu | nap2s.admin |
| `/nap2s reload` | Tải lại cấu hình | nap2s.admin |
| `/nap2s report [today\|week\|month]` | Báo cáo doanh thu | nap2s.admin |
| `/nap2s setkey <key> <value>` | Lưu API key mã hóa vào DB | nap2s.admin |
| `/nap2s napthucong <player> <xu>` | Cộng xu thủ công | nap2s.admin |
| `/nap2s tracuugd <mã>` | Tra cứu giao dịch | nap2s.admin |
| `/nap2s lichsu` | Xem lịch sử nạp | nap2s.admin |
| `/nap2s auditlog` | Xem nhật ký thao tác admin | nap2s.admin |

**Placeholder:**

Dùng với PlaceholderAPI để hiển thị top nạp trên scoreboard, hologram, tab...

| Placeholder | Chức năng | Ghi chú |
|---|---|---|
| `%NAP2S_TOP_DONATE_TOTAL_X_PLAYER%` | Tên người chơi đứng top X | Đổi `TOTAL` thành `WEEK` hoặc `MONTH` để lấy top tuần/tháng |
| `%NAP2S_TOP_DONATE_TOTAL_X_VALUE%` | Số tiền nạp của top X | X là thứ hạng (1, 2, 3...) |
| `%NAP2S_DATA_DONATE_TOTAL%` | Tổng tiền đã nạp của người chơi | Đổi `TOTAL` thành `WEEK` hoặc `MONTH` cho tuần/tháng |

> Ví dụ: `%NAP2S_TOP_DONATE_TOTAL_1_PLAYER%` hiển thị tên người đứng top 1 nạp thẻ

**Cấu trúc thư mục:**

```
plugins/Nap2S/
├── menu/
│   ├── napthe/
│   │   ├── loaithe.yml          # Giao diện chọn loại thẻ
│   │   └── menhgia.yml          # Giao diện chọn mệnh giá
│   └── admin/
│       └── dashboard.yml        # Giao diện quản trị
├── providers/
│   └── default.yml              # Cổng nạp thẻ mặc định
├── config.yml                   # Cài đặt chung
├── banking.yml                  # Cài đặt chuyển khoản
├── providers.yml                # Danh sách cổng nạp thẻ
├── messages.yml                 # Tin nhắn trong game
├── mocnap.yml                   # Mốc nạp cá nhân
├── mocnaptong.yml               # Mốc nạp tổng server
├── khuyenmai.yml                # Lịch khuyến mãi
└── discord.yml                  # Thông báo Discord
```

- Giao diện nạp thẻ chỉnh tại `loaithe.yml` và `menhgia.yml`
- API key cổng nạp thẻ thêm tại file trong `providers/`, hoặc dùng lệnh `/nap2s setkey` để lưu an toàn hơn
- Cài đặt chung tại `config.yml`, tin nhắn tại `messages.yml`
- Nạp chuyển khoản cấu hình tại `banking.yml`
- Mốc nạp và phần thưởng tại `mocnap.yml`

Webhook
===========

Plugin có máy chủ web nhỏ tích hợp sẵn để nhận thông báo thanh toán:

| Cổng | Địa chỉ webhook |
|---|---|
| SePay | `https://tên-miền-của-bạn/nap2s/ipn` |
| PayOS | `https://tên-miền-của-bạn/nap2s/ipn/payos` |

Cần mở cổng mạng (mặc định 3510) hoặc dùng reverse proxy nếu server nằm sau NAT.

Hình ảnh
===========

**/napthe**
===========
<p align="center">
  <img src="https://github.com/user-attachments/assets/9a175015-581c-4769-b95a-771c8d235a11" width="500"/>
  <img src="https://github.com/user-attachments/assets/3b082a0f-fa8b-4bf5-ae2c-f31863897953" width="500"/>
</p>

**/napatm**
===========
<p align="center">
  <img src="https://github.com/user-attachments/assets/b7245720-be1e-45a7-b824-7f24d0916ada" width="500"/>
  <img src="https://github.com/user-attachments/assets/db1c5bb8-fd4f-455c-8bc0-57f4e61600d2" width="500"/>
  <img src="https://github.com/user-attachments/assets/f900b4fe-3053-4b8d-8d22-8bcb950bb367" width="500"/>
  <img src="https://github.com/user-attachments/assets/b7a5ab8f-d5a0-4d44-9be7-86447ccd385b" width="500"/>
</p>

**/napthe lishsu**
===========
<p align="center">
  <img src="https://github.com/user-attachments/assets/6b423b35-8e05-4af0-8ed6-1315e9cd27e6" width="500"/>
  <img src="https://github.com/user-attachments/assets/8bd1c4ab-18d8-4dc8-bb7c-3e7f8aa545e0" width="500"/>
</p>

**Bảo mật / mã hóa dữ liệu**
===========
<p align="center">
  <img src="https://github.com/user-attachments/assets/75044e58-7805-458c-baea-035cf7cec56e" width="500"/>
  <img src="https://github.com/user-attachments/assets/f1871be8-f81d-4cfb-a833-cd4546efb675" width="500"/>
  <img src="https://github.com/user-attachments/assets/c8046806-6144-4a53-9dc3-c99c1ef363c6" width="500"/>
  <img src="https://github.com/user-attachments/assets/e3419ac3-e0ff-48bc-b262-7a5ebd7a0b96" width="500"/>
</p>


**Cấu hình tự động xoay cổng**
===========

---

Plugin được phát triển dựa trên nền tảng [DotMan](https://github.com/minevn/dotman) và lấy ý tưởng từ các plugin nạp thẻ hiện nay, bổ sung thêm nạp chuyển khoản, giao diện Bedrock, bảo mật API key, và quản lý doanh thu.
