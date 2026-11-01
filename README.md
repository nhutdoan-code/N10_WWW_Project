# PhoneNest

Website bán điện thoại và phụ kiện.

## Công nghệ sử dụng

### Backend
- Java
- Spring Boot
- Spring Data JPA
- MariaDB

### Frontend
- React

## Cấu trúc project

- `BE/` - Backend
- `FE/` - Frontend
- `DB/` - Database
- `API_DOCUMENTATION.md` - Tài liệu API

## Thanh toán MoMo

Thanh toán ví MoMo dùng môi trường test. Đặt các biến môi trường
`MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_REDIRECT_URL` và
`MOMO_IPN_URL` trước khi chọn phương thức ví điện tử. `MOMO_IPN_URL` phải là URL
HTTPS công khai để MoMo gọi được; hệ thống xác thực chữ ký và số tiền từ đơn hàng
trước khi ghi nhận thanh toán.

## Thành viên

- Nguyễn Nhựt Đoàn
- Nguyễn Huy Hoàng
- Lưu Văn Quốc
- Hồ Công Hoàng
- Lê Minh Phúc

## Cách chạy project

### Backend
Mở project trong thư mục `BE/phonenest` và chạy Spring Boot.

### Frontend

```bash
cd FE
npm install
npm run dev
