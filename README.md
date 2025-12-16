# VNPay Spring Boot Demo

## 📌 Mô tả

Dự án demo tích hợp **cổng thanh toán VNPay** vào ứng dụng **Spring Boot**, phục vụ mục đích học tập và tham khảo.

---

## ✅ Chức năng hiện có

* Tạo yêu cầu thanh toán VNPay từ backend Spring Boot
* Redirect người dùng sang cổng thanh toán VNPay (Sandbox)
* Nhận kết quả thanh toán qua **Return URL**
* Xác thực checksum (hash) từ VNPay
* Demo giao diện thanh toán đơn giản

---

## 🛠 Công nghệ sử dụng

* Java
* Spring Boot
* Maven
* VNPay Payment Gateway
* HTML (demo)

---

## ▶️ Chạy dự án

### Chạy trên IntelliJ IDEA

1. Mở project bằng **IntelliJ IDEA**
2. Đợi IntelliJ load Maven dependencies
3. Mở file `Application.java`
4. Chọn **Run ▶️** (hoặc `Shift + F10`)

Ứng dụng chạy tại: `http://localhost:8080`

---

## ⚠️ Lưu ý

* Chỉ phù hợp cho **demo / học tập**
* Chưa có test tự động
* Chưa xử lý IPN & production security

---

## 👨‍💻 Tác giả

SangPepsi
