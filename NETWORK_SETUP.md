# 🌐 Hướng dẫn cấu hình mạng để 2 máy nhắn tin với nhau

## 📋 Yêu cầu
- 2 máy tính trong cùng mạng LAN (hoặc qua Internet với port forwarding)
- Java 17+ cài đặt trên cả 2 máy
- MySQL/XAMPP chạy trên máy Server
- Firewall cho phép kết nối qua port RMI

---

## 🖥️ Cấu hình Máy A (Server)

### Bước 1: Kiểm tra IP của máy
**Windows:**
```cmd
ipconfig
```
Tìm dòng `IPv4 Address`, ví dụ: `192.168.1.100`

**Linux/Mac:**
```bash
ifconfig
# hoặc
ip addr show
```

### Bước 2: Cấu hình `config.properties`
Sửa file `config.properties` trên **máy Server**:

```properties
# Database Configuration - Chỉ cần trên máy Server
db.url=jdbc:mysql://localhost:3306/chat_app?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
db.username=root
db.password=
db.driver=com.mysql.cj.jdbc.Driver
db.pool.size=10

# Server Configuration
# QUAN TRỌNG: Thay localhost bằng IP thực của máy này
server.host=192.168.1.100
server.rmi.port=1099
server.chat.port=8888
server.video.port=9999

# Client Configuration (không dùng trên máy Server)
client.rmi.registry=localhost
client.rmi.port=1099

# Application Configuration
app.name=Metus Chat
app.version=1.0.0
```

**Lưu ý:** Thay `192.168.1.100` bằng IP thực của máy Server của bạn!

### Bước 3: Mở Firewall
**Windows:**
```cmd
# Mở Windows Defender Firewall
# Hoặc dùng PowerShell (Run as Administrator):
netsh advfirewall firewall add rule name="RMI Server" dir=in action=allow protocol=TCP localport=1099
netsh advfirewall firewall add rule name="Chat Server" dir=in action=allow protocol=TCP localport=8888
netsh advfirewall firewall add rule name="Video Server" dir=in action=allow protocol=TCP localport=9999
```

**Linux:**
```bash
sudo ufw allow 1099/tcp
sudo ufw allow 8888/tcp
sudo ufw allow 9999/tcp
```

### Bước 4: Khởi động Server
```cmd
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
```

Bạn sẽ thấy log:
```
Chat Server Started Successfully!
RMI Registry: 192.168.1.100:1099
For remote access, clients should connect to: 192.168.1.100
Make sure firewall allows port 1099 (TCP)
```

---

## 💻 Cấu hình Máy B (Client)

### Bước 1: Cấu hình `config.properties`
Sửa file `config.properties` trên **máy Client**:

```properties
# Database Configuration - KHÔNG CẦN trên máy Client
# (có thể comment hoặc để nguyên, không dùng)

# Server Configuration - KHÔNG CẦN trên máy Client
server.host=localhost
server.rmi.port=1099
server.chat.port=8888
server.video.port=9999

# Client Configuration
# QUAN TRỌNG: Thay localhost bằng IP của máy Server
client.rmi.registry=192.168.1.100
client.rmi.port=1099

# Application Configuration
app.name=Metus Chat
app.version=1.0.0
```

**Lưu ý:** `192.168.1.100` là IP của máy Server (Máy A)!

### Bước 2: Khởi động Client
```cmd
mvn clean compile
mvn javafx:run
```

---

## 🔄 Kịch bản sử dụng

### Máy A (Server)
1. ✅ Chạy MySQL/XAMPP
2. ✅ Khởi động Server: `mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"`
3. ✅ Khởi động Client (nếu muốn chat trên máy này): `mvn javafx:run`
4. ✅ Đăng nhập với user A (ví dụ: `alice`)

### Máy B (Client)
1. ✅ Khởi động Client: `mvn javafx:run`
2. ✅ Đăng nhập với user B (ví dụ: `bob`)
3. ✅ Nhắn tin với `alice` → Máy A sẽ nhận được tin nhắn!

---

## 🧪 Kiểm tra kết nối

### Test 1: Ping máy Server
Từ máy Client, kiểm tra kết nối mạng:
```cmd
ping 192.168.1.100
```
Phải thấy reply thành công.

### Test 2: Test port RMI
Từ máy Client, kiểm tra port RMI:
```cmd
telnet 192.168.1.100 1099
```
Nếu kết nối được → OK!

Nếu không có `telnet`, cài đặt:
**Windows:**
```
Control Panel → Programs → Turn Windows features on or off → Telnet Client
```

### Test 3: Kiểm tra log
**Server log:**
```
Connected to RMI registry at 192.168.1.100:1099
```

**Client log:**
```
Connected to RMI registry at 192.168.1.100:1099
```

---

## 🚨 Xử lý lỗi thường gặp

### Lỗi 1: `Connection refused`
**Nguyên nhân:** Firewall chặn hoặc Server chưa chạy.

**Giải pháp:**
1. Kiểm tra Server đã chạy chưa
2. Kiểm tra firewall trên máy Server
3. Thử tắt firewall tạm thời để test

### Lỗi 2: `java.rmi.ConnectException: Connection refused to host: 127.0.0.1`
**Nguyên nhân:** RMI vẫn dùng localhost thay vì IP thực.

**Giải pháp:**
1. Kiểm tra `server.host` trong `config.properties` phải là IP thực (không phải localhost)
2. Restart Server sau khi sửa config

### Lỗi 3: `NotBoundException: ChatService`
**Nguyên nhân:** Client kết nối được registry nhưng service chưa bind.

**Giải pháp:**
1. Kiểm tra Server log có dòng "ChatService bound: ChatService"
2. Restart Server

### Lỗi 4: Kết nối được nhưng không nhận tin nhắn
**Nguyên nhân:** Callback không hoạt động do firewall chặn kết nối ngược từ Server về Client.

**Giải pháp:**
1. Mở firewall trên **cả 2 máy** (Client và Server)
2. Hoặc tắt firewall tạm thời để test

---

## 🌍 Kết nối qua Internet (nâng cao)

Nếu 2 máy không cùng mạng LAN, cần:

1. **Port Forwarding trên Router:**
   - Forward port `1099` → IP máy Server
   - Forward port `8888` → IP máy Server
   - Forward port `9999` → IP máy Server

2. **Sử dụng IP Public:**
   - Kiểm tra IP public: https://whatismyipaddress.com/
   - Cấu hình `server.host` = IP public
   - Cấu hình `client.rmi.registry` = IP public

3. **Hoặc dùng VPN:**
   - Hamachi, ZeroTier, Tailscale
   - Tạo mạng ảo để 2 máy "cùng LAN"

---

## ✅ Checklist

### Máy Server (Máy A):
- [ ] MySQL/XAMPP đang chạy
- [ ] `server.host` = IP thực của máy (không phải localhost)
- [ ] Firewall mở port 1099, 8888, 9999
- [ ] Server đã khởi động thành công
- [ ] Log hiển thị "Chat Server Started Successfully!"

### Máy Client (Máy B):
- [ ] `client.rmi.registry` = IP của máy Server
- [ ] Có thể ping được máy Server
- [ ] Client khởi động thành công
- [ ] Log hiển thị "Connected to RMI registry at [IP máy Server]"

### Test cuối:
- [ ] Đăng nhập 2 user khác nhau trên 2 máy
- [ ] Gửi tin nhắn từ máy A → máy B nhận được
- [ ] Gửi tin nhắn từ máy B → máy A nhận được
- [ ] Tạo group, add member, nhắn tin group
- [ ] Status online/offline cập nhật đúng

---

## 📞 Ví dụ cụ thể

### Máy A (Server): IP = 192.168.1.100
**config.properties:**
```properties
server.host=192.168.1.100
client.rmi.registry=localhost
```

**Chạy:**
```cmd
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
mvn javafx:run  # (nếu muốn chat trên máy này)
```

### Máy B (Client): IP = 192.168.1.200
**config.properties:**
```properties
client.rmi.registry=192.168.1.100
```

**Chạy:**
```cmd
mvn javafx:run
```

### Kết quả:
- Máy A login: `alice`
- Máy B login: `bob`
- `bob` gửi tin "Hello Alice!" → `alice` nhận được ngay lập tức! ✅

---

## 🎉 Hoàn tất!

Bây giờ bạn đã có thể nhắn tin giữa 2 máy khác nhau! 🚀

Nếu gặp vấn đề, kiểm tra lại:
1. IP có đúng không?
2. Firewall có mở không?
3. Server có chạy không?
4. Log có báo lỗi gì không?

