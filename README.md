<img width="2227" height="859" alt="logo2s" src="https://github.com/user-attachments/assets/ccf8a5d6-c838-425b-a1ee-f49b6132fecb" />

Nap2S ![Supported server version](https://img.shields.io/badge/minecraft-1.21+-brightgreen) ![Bedrock](https://img.shields.io/badge/bedrock-hỗ%20trợ-orange)
===========

Plugin nạp thẻ cào và chuyển khoản ngân hàng cho server Minecraft. Hỗ trợ nhiều cổng nạp, quét QR ngay trong game, giao diện riêng cho Bedrock/PE, và bộ công cụ quản trị doanh thu cho admin.

**Cổng nạp thẻ:** ChargingWS-v2, Card2K, TheSieuRe, Gachthe1s,...

**Cổng chuyển khoản:** [SePay](https://sepay.vn/) (VietQR), [PayOS](https://payos.vn/)

Tính năng
===========

**Nạp thẻ cào**
- Hỗ trợ nhiều cổng nạp thẻ, có thể cấu hình route chính/phụ
- Hỗ trợ định tuyến theo `best-rate` hoặc `priority`
- Khuyến mãi riêng theo loại thẻ
- Giao diện menu tùy biến qua file YAML
- Hỗ trợ lưu key an toàn bằng lệnh `/nap2s setkey`

**Nạp chuyển khoản**
- Tạo mã QR tức thì cho giao dịch
- Hỗ trợ tất cả ngân hàng Việt Nam qua VietQR
- Có trang thanh toán riêng mở trên trình duyệt
- Tự nhận biết khi người chơi đã chuyển tiền và cộng xu
- Đếm ngược thời gian hết hạn đơn
- Hỗ trợ chọn nhanh mệnh giá hoặc nhập số tiền tùy chỉnh

**Bedrock / PE**
- Giao diện riêng cho người chơi Bedrock bằng native form
- Form nạp thẻ: chọn loại thẻ, mệnh giá, nhập seri và mã thẻ
- Form chuyển khoản: chọn nhanh hoặc nhập số tiền
- Xem lịch sử giao dịch: danh sách → bấm để xem chi tiết
- Hiển thị khuyến mãi ngay trên form
- Tự nhận biết người chơi Bedrock qua Floodgate

**Quản trị**
- API key và key nhạy cảm có thể lưu trong database
- Ghi lại thao tác admin
- Báo cáo doanh thu theo ngày, tuần, tháng
- Kiểm tra cấu hình khi khởi động
- Giới hạn số lần nạp và tổng tiền nạp mỗi ngày để chống lạm dụng

**Mốc nạp & Top nạp**
- Top nạp toàn thời gian, theo tuần, theo tháng
- Mốc nạp cá nhân
- Mốc nạp tổng toàn server
- Thanh tiến độ hiển thị trên BossBar
- Hỗ trợ nhiều server dùng chung database

**Bảo vệ bản quyền**
- Kích hoạt bằng `license-key`
- Mỗi server có `server-id` riêng
- Xác thực online khi khởi động
- Hỗ trợ quản lý license riêng cho từng khách hàng

Hướng dẫn sử dụng
===========

**Yêu cầu**
- [Paper](https://papermc.io/) 1.21+
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) — quản lý xu
- (Tùy chọn) [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — hiển thị top nạp
- (Tùy chọn) [Floodgate](https://geysermc.org/) — giao diện riêng cho Bedrock

**Cài đặt**
1. Bỏ `Nap2S.jar` vào thư mục `plugins/`
2. Khởi động server để plugin tạo file cấu hình mẫu và sinh `server-id`
3. Gửi `server-id` cho admin để nhận `license-key`
4. Điền `license-key` vào `config.yml`
5. Khởi động lại server
6. Chạy `/nap2s setup` trong game để xem hướng dẫn cấu hình từng bước
7. Hoàn tất cấu hình, sau đó dùng `/nap2s reload`

**Lệnh**
| Lệnh | Chức năng | Quyền |
|---|---|---|
| `/napthe` | Mở giao diện nạp thẻ | Người chơi |
| `/napthe <loại> <giá> <seri> <mã>` | Nạp thẻ nhanh | Người chơi |
| `/napthe lichsu` | Xem lịch sử nạp thẻ | Người chơi |
| `/napatm` | Mở giao diện chuyển khoản | Người chơi |
| `/napatm lichsu` | Xem lịch sử chuyển khoản | Người chơi |
| `/napatm status` | Kiểm tra đơn đang chờ | Người chơi |
| `/napatm cancel` | Hủy đơn đang chờ | Người chơi |
| `/topnap` | Xem bảng xếp hạng nạp | `nap2s.topnap` |
| `/nap2s setup` | Hướng dẫn cài đặt lần đầu | `nap2s.admin` |
| `/nap2s reload` | Tải lại cấu hình | `nap2s.admin` |
| `/nap2s report [today\|week\|month]` | Báo cáo doanh thu | `nap2s.admin` |
| `/nap2s setkey <provider-id> <field> <value>` | Lưu key cổng nạp an toàn | `nap2s.admin` |
| `/nap2s setkey <field> <value>` | Lưu key banking an toàn | `nap2s.admin` |
| `/nap2s napthucong <player> <xu>` | Cộng xu thủ công | `nap2s.admin` |
| `/nap2s tracuugd <mã>` | Tra cứu giao dịch | `nap2s.admin` |
| `/nap2s lichsu` | Xem lịch sử nạp | `nap2s.admin` |
| `/nap2s auditlog` | Xem nhật ký thao tác admin | `nap2s.admin` |

Placeholder
===========

Dùng với PlaceholderAPI để hiển thị top nạp trên scoreboard, hologram, tab...

| Placeholder | Chức năng | Ghi chú |
|---|---|---|
| `%NAP2S_TOP_DONATE_TOTAL_X_PLAYER%` | Tên người chơi đứng top X | Đổi `TOTAL` thành `WEEK` hoặc `MONTH` để lấy top tuần/tháng |
| `%NAP2S_TOP_DONATE_TOTAL_X_VALUE%` | Số tiền nạp của top X | X là thứ hạng |
| `%NAP2S_DATA_DONATE_TOTAL%` | Tổng tiền đã nạp của người chơi | Đổi `TOTAL` thành `WEEK` hoặc `MONTH` cho tuần/tháng |

> Ví dụ: `%NAP2S_TOP_DONATE_TOTAL_1_PLAYER%`

Cấu trúc thư mục
===========

```text
plugins/Nap2S/
├── data/
│   └── server-id.dat
├── menu/
│   ├── napthe/
│   │   ├── loaithe.yml
│   │   └── menhgia.yml
│   └── admin/
│       └── dashboard.yml
├── providers/
│   └── default.yml
├── config.yml
├── banking.yml
├── providers.yml
├── messages.yml
├── mocnap.yml
├── mocnaptong.yml
├── khuyenmai.yml
└── discord.yml
```

**Ghi chú**
- Giao diện nạp thẻ chỉnh tại `menu/napthe/`
- Dashboard admin chỉnh tại `menu/admin/dashboard.yml`
- Cấu hình banking tại `banking.yml`
- Danh sách cổng nạp tại `providers.yml`
- Mốc nạp tại `mocnap.yml` và `mocnaptong.yml`
- Nên dùng `/nap2s setkey` để lưu key an toàn hơn thay vì để trực tiếp trong file

Webhook
===========

Plugin có webserver tích hợp để nhận thông báo thanh toán:

| Cổng | Địa chỉ webhook |
|---|---|
| SePay | `https://ten-mien-cua-ban/nap2s/ipn` |
| PayOS | `https://ten-mien-cua-ban/nap2s/ipn/payos` |

Nếu server nằm sau NAT hoặc private network, cần mở cổng hoặc dùng reverse proxy / tunnel.

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

**/napatm lichsu**
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

---

Nap2S là plugin premium. Repository công khai chỉ dùng để giới thiệu tính năng, hướng dẫn cài đặt và cập nhật thông tin sản phẩm.
