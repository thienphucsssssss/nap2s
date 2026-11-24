# Hướng Dẫn Cài Đặt Nap2S

Tài liệu này dành cho người mới. Bạn không cần biết lập trình để làm theo.

## 1. Chép plugin vào server

Dùng Paper 1.21+ và Java 21. Bạn cần có:

- `Nap2S.jar`
- `PlayerPoints.jar`

Chép các file đó vào thư mục `plugins` của server.

## 2. Khởi động server lần đầu

Sau khi mở server, plugin sẽ tự tạo thư mục:

- `plugins/Nap2S`

Trong đó có các file quan trọng:

- `config.yml`
- `providers.yml`
- `banking.yml`
- `messages.yml`

## 3. Nếu bạn muốn dùng nạp thẻ

Mở `providers.yml` và làm 3 việc:

1. bật cổng bạn muốn dùng bằng cách đổi `enabled: true`
2. điền `partner-id`
3. điền `partner-key`

Sau đó mở `config.yml` để chỉnh:

- số xu theo từng mệnh giá
- lệnh thưởng
- tên server

## 4. Nếu bạn muốn dùng ATM/QR

Mở `banking.yml` và điền:

- `enabled: true`
- `webserver-public-url`
- `sepay-api-token`
- `sepay-secret-key`
- `bank-code`
- `bank-name`
- `bank-account-number`
- `bank-account-holder`

Ví dụ:

```yml
enabled: true
webserver-public-url: 'https://pay.example.com'
sepay-api-token: 'token-cua-ban'
sepay-secret-key: 'secret-cua-ban'
bank-code: 'MB'
bank-name: 'MB Bank'
bank-account-number: '0123456789'
bank-account-holder: 'NGUYEN VAN A'
```

## 5. Chỉnh thưởng

Trong `config.yml`:

- `donate-amounts` là số xu nhận được
- `donate-commands` là lệnh chạy thêm

Trong `banking.yml`:

- `reward-bonus-percent` là phần thưởng thêm cho ATM/QR

## 6. Khởi động lại server

Sau khi chỉnh xong, bạn hãy:

- khởi động lại server

hoặc dùng:

- `/nap2s reload`

## 7. Cách kiểm tra nhanh

Người chơi:

- `/napthe`
- `/napatm`

Admin:

- `/nap2s quanly`
- `/nap2s tracuugd <mã giao dịch>`
- `/nap2s history`

## 8. Nếu ATM/QR không hoạt động

Bạn hãy kiểm tra:

1. `banking.yml` đã bật `enabled: true` chưa
2. `webserver-public-url` đã điền chưa
3. token và secret đã đúng chưa
4. đường dẫn công khai có đang mở được không
5. webhook trên SePay có đang trỏ đúng về plugin không

## 9. Nếu nạp thẻ không hoạt động

Bạn hãy kiểm tra:

1. cổng nạp đã bật chưa
2. id và key đã đúng chưa
3. loại thẻ đó có đang được bật không
4. phần `provider` trong `config.yml` có đúng không
