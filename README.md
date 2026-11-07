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
```

## Cấu hình AI (Ollama Local)

PhoneNest sử dụng nền tảng AI nội bộ chạy hoàn toàn cục bộ qua **Ollama**, không phụ thuộc vào bất kỳ dịch vụ cloud bên thứ ba nào (như Gemini, OpenAI hay Groq):
- **Embedding Model:** `bge-m3` (BAAI BGE-M3 Multilingual Embedding, 1024 dimensions)
- **Base URL:** `http://127.0.0.1:11434`
- **Chức năng:** Tìm kiếm ngữ nghĩa (Semantic Product Search), trích xuất đặc tả kỹ thuật và tư vấn bán hàng thông minh kèm hồ sơ sở thích khách hàng (User Preference Context).

### Cài đặt Ollama cho môi trường mới:
1. Tải và cài đặt Ollama từ [https://ollama.com](https://ollama.com).
2. Tải model embedding `bge-m3`:
   ```bash
   ollama pull bge-m3
   ```
3. Đảm bảo Ollama server đang chạy (mặc định cổng `11434`).
4. Khi khởi động PhoneNest Backend, hệ thống sẽ tự động kết nối và lập chỉ mục Vector Store. Nếu Ollama chưa bật, hệ thống tự động kích hoạt chế độ fallback từ khóa thông minh mà không gây lỗi ứng dụng.
