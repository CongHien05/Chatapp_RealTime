# 🚀 Hướng dẫn nhanh: Cấu hình 2 máy nhắn tin

## 📦 Files quan trọng
- `NETWORK_SETUP.md` - Hướng dẫn chi tiết đầy đủ
- `config.properties.server.example` - Mẫu config cho máy Server
- `config.properties.client.example` - Mẫu config cho máy Client
- `start-server.bat` / `start-server.sh` - Script khởi động Server
- `start-client.bat` / `start-client.sh` - Script khởi động Client

---

## ⚡ Cấu hình nhanh (3 bước)

### 🖥️ Máy A (Server)

#### Bước 1: Kiểm tra IP
```cmd
ipconfig
```
Ví dụ: `192.168.212.103`

#### Bước 2: Sửa `config.properties`
```properties
server.host=192.168.212.103
client.rmi.registry=localhost
```

#### Bước 3: Mở Firewall (Windows)
```cmd
netsh advfirewall firewall add rule name="RMI Server" dir=in action=allow protocol=TCP localport=1099
```

#### Bước 4: Chạy Server
Click đúp vào `start-server.bat` hoặc:
```cmd
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
```

---

### 💻 Máy B (Client)

#### Bước 1: Sửa `config.properties`
```properties
client.rmi.registry=192.168.212.103
```
*(Thay bằng IP của máy Server)*

#### Bước 2: Chạy Client
Click đúp vào `start-client.bat` hoặc:
```cmd
mvn javafx:run
```

---

## ✅ Test

1. **Máy A**: Đăng nhập user `alice`
2. **Máy B**: Đăng nhập user `bob`
3. **Máy B**: Gửi tin "Hello!" cho `alice`
4. **Máy A**: Nhận được tin nhắn ngay lập tức! ✅

---

## 🚨 Lỗi thường gặp

### Lỗi: Connection refused
**Giải pháp:**
1. Kiểm tra Server đã chạy chưa
2. Kiểm tra IP có đúng không
3. Tắt Firewall tạm thời để test

### Lỗi: NotBoundException
**Giải pháp:**
1. Restart Server
2. Kiểm tra log Server có "ChatService bound" không

---

## 📖 Đọc thêm
Xem `NETWORK_SETUP.md` để biết chi tiết đầy đủ về:
- Cấu hình Firewall
- Kết nối qua Internet
- Xử lý lỗi chi tiết
- Port forwarding
- VPN setup

---

## 🎯 Tóm tắt

| Máy | IP | Config | Chạy |
|-----|-----|--------|------|
| **Server** | `192.168.212.103` | `server.host=192.168.212.103` | `start-server.bat` |
| **Client** | `192.168.x.x` | `client.rmi.registry=192.168.212.103` | `start-client.bat` |

**Lưu ý:** Thay `192.168.212.103` bằng IP thực của máy Server của bạn!

