# DanBaiNoSo Chat - Hệ thống Chat Real-time

## 📋 Tổng quan dự án

Đây là một ứng dụng chat real-time được xây dựng bằng Java với các công nghệ:
- **JavaFX**: Giao diện người dùng
- **RMI (Remote Method Invocation)**: Giao tiếp giữa client và server
- **MySQL**: Cơ sở dữ liệu
- **HikariCP**: Connection pooling cho database
- **BCrypt**: Mã hóa mật khẩu

## 🏗️ Cấu trúc Project

```
danbainoso/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/example/danbainoso/
│   │   │       ├── client/          # Client-side code
│   │   │       │   ├── ClientMain.java
│   │   │       │   ├── ClientRMI.java
│   │   │       │   ├── MediaHandler.java
│   │   │       │   └── ui/          # UI Controllers
│   │   │       │       ├── LoginController.java
│   │   │       │       ├── ChatController.java
│   │   │       │       └── GroupController.java
│   │   │       ├── server/          # Server-side code
│   │   │       │   ├── ServerMain.java
│   │   │       │   ├── ChatServiceImpl.java
│   │   │       │   └── VideoServiceImpl.java
│   │   │       ├── database/        # Database access layer
│   │   │       │   ├── DatabaseConnection.java
│   │   │       │   ├── UserDAO.java
│   │   │       │   ├── MessageDAO.java
│   │   │       │   └── GroupDAO.java
│   │   │       ├── shared/          # Shared interfaces & models
│   │   │       │   ├── ChatService.java
│   │   │       │   ├── VideoService.java
│   │   │       │   ├── ChatClientCallback.java
│   │   │       │   ├── VideoClientCallback.java
│   │   │       │   └── models/
│   │   │       │       ├── User.java
│   │   │       │       ├── Message.java
│   │   │       │       ├── Group.java
│   │   │       │       └── CallRequest.java
│   │   │       └── utils/           # Utility classes
│   │   │           ├── Config.java
│   │   │           ├── EncryptionUtil.java
│   │   │           └── LoggerUtil.java
│   │   └── resources/
│   │       ├── db/
│   │       │   └── schema.sql       # Database schema
│   │       ├── css/
│   │       │   └── style.css        # UI styling
│   │       └── org/example/danbainoso/client/ui/
│   │           ├── login.fxml
│   │           ├── chat.fxml
│   │           └── group.fxml
│   └── test/
├── config.properties                 # Configuration file
├── pom.xml                           # Maven dependencies
└── README.md                         # This file
```

## 📝 Từng công đoạn xây dựng project

### **Công đoạn 1: Thiết kế Database Schema**

**File:** `src/main/resources/db/schema.sql`

**Chức năng:**
- Tạo các bảng: `users`, `groups`, `messages`, `group_members`, `friendships`
- Định nghĩa quan hệ giữa các bảng (Foreign Keys)
- Tạo indexes để tối ưu truy vấn
- Insert dữ liệu mẫu để test

**Các bảng chính:**
1. **users**: Lưu thông tin người dùng (username, password, email, status...)
2. **groups**: Lưu thông tin nhóm chat
3. **messages**: Lưu tin nhắn (private và group messages)
4. **group_members**: Bảng trung gian cho quan hệ many-to-many giữa users và groups
5. **friendships**: Lưu quan hệ bạn bè (chưa implement đầy đủ)

**Cách sử dụng:**
```sql
-- Chạy file schema.sql trong MySQL để tạo database
mysql -u root -p < src/main/resources/db/schema.sql
-- Hoặc import vào MySQL Workbench
```

---

### **Công đoạn 2: Cấu hình Dependencies (pom.xml)**

**File:** `pom.xml`

**Chức năng:**
- Khai báo tất cả thư viện cần thiết:
  - JavaFX (controls, fxml, media)
  - MySQL Connector
  - HikariCP (connection pooling)
  - BCrypt (password hashing)
  - SLF4J & Logback (logging)
  - Gson (JSON processing)

**Các dependencies quan trọng:**
- `javafx-controls`, `javafx-fxml`: Giao diện JavaFX
- `mysql-connector-j`: Kết nối MySQL
- `HikariCP`: Quản lý connection pool
- `jbcrypt`: Mã hóa mật khẩu

---

### **Công đoạn 3: Tạo Model Classes**

**Files:** 
- `src/main/java/org/example/danbainoso/shared/models/User.java`
- `src/main/java/org/example/danbainoso/shared/models/Message.java`
- `src/main/java/org/example/danbainoso/shared/models/Group.java`
- `src/main/java/org/example/danbainoso/shared/models/CallRequest.java`

**Chức năng:**
- Định nghĩa cấu trúc dữ liệu cho các entity
- Implement `Serializable` để có thể truyền qua RMI
- Các getter/setter methods
- Helper methods (ví dụ: `isGroupMessage()`, `isPrivateMessage()`)

**Ví dụ User model:**
```java
public class User implements Serializable {
    private int userId;
    private String username;
    private String password; // Hashed
    private UserStatus status; // ONLINE, OFFLINE, AWAY, BUSY
    // ... các fields khác
}
```

---

### **Công đoạn 4: Utility Classes**

**Files:**
- `src/main/java/org/example/danbainoso/utils/Config.java`
- `src/main/java/org/example/danbainoso/utils/EncryptionUtil.java`
- `src/main/java/org/example/danbainoso/utils/LoggerUtil.java`

**Chức năng:**

1. **Config.java**: Đọc cấu hình từ `config.properties`
   - Database connection settings
   - Server host/port
   - Client settings

2. **EncryptionUtil.java**: Mã hóa mật khẩu
   - `hashPassword()`: Hash password bằng BCrypt
   - `verifyPassword()`: Verify password với hash
   - `generateToken()`: Tạo token ngẫu nhiên

3. **LoggerUtil.java**: Wrapper cho SLF4J logger
   - Tạo logger cho từng class
   - Helper methods cho log levels

---

### **Công đoạn 5: Database Connection & DAO**

**Files:**
- `src/main/java/org/example/danbainoso/database/DatabaseConnection.java`
- `src/main/java/org/example/danbainoso/database/UserDAO.java`
- `src/main/java/org/example/danbainoso/database/MessageDAO.java`
- `src/main/java/org/example/danbainoso/database/GroupDAO.java`

**Chức năng:**

1. **DatabaseConnection.java**:
   - Khởi tạo HikariCP connection pool
   - Cung cấp `getConnection()` để lấy connection từ pool
   - Quản lý lifecycle của connection pool

2. **UserDAO.java**:
   - `createUser()`: Tạo user mới (hash password)
   - `getUserById()`, `getUserByUsername()`: Lấy user
   - `authenticate()`: Xác thực đăng nhập
   - `updateUserStatus()`: Cập nhật trạng thái
   - `searchUsers()`: Tìm kiếm user

3. **MessageDAO.java**:
   - `createMessage()`: Lưu tin nhắn
   - `getPrivateMessages()`: Lấy tin nhắn private
   - `getGroupMessages()`: Lấy tin nhắn nhóm
   - `markMessagesAsRead()`: Đánh dấu đã đọc
   - `getUnreadCount()`: Đếm tin nhắn chưa đọc

4. **GroupDAO.java**:
   - `createGroup()`: Tạo nhóm mới
   - `getUserGroups()`: Lấy danh sách nhóm của user
   - `addMember()`, `removeMember()`: Quản lý thành viên
   - `getGroupMembers()`: Lấy danh sách thành viên

---

### **Công đoạn 6: Shared Interfaces (RMI)**

**Files:**
- `src/main/java/org/example/danbainoso/shared/ChatService.java`
- `src/main/java/org/example/danbainoso/shared/VideoService.java`
- `src/main/java/org/example/danbainoso/shared/ChatClientCallback.java`
- `src/main/java/org/example/danbainoso/shared/VideoClientCallback.java`

**Chức năng:**

1. **ChatService.java**: Interface RMI cho chat service
   - User operations: `login()`, `register()`, `updateUserStatus()`
   - Message operations: `sendMessage()`, `getPrivateMessages()`, `getGroupMessages()`
   - Group operations: `createGroup()`, `getUserGroups()`, `addMemberToGroup()`
   - Callback registration: `registerClient()`, `unregisterClient()`

2. **VideoService.java**: Interface RMI cho video call service
   - `initiateCall()`, `acceptCall()`, `rejectCall()`, `endCall()`
   - Callback registration cho real-time updates

3. **ChatClientCallback.java**: Callback interface cho client
   - `onMessageReceived()`: Nhận tin nhắn mới
   - `onUserStatusChanged()`: Cập nhật trạng thái user
   - `onUserJoinedGroup()`, `onUserLeftGroup()`: Thông báo thành viên

4. **VideoClientCallback.java**: Callback interface cho video call
   - `onIncomingCall()`, `onCallAccepted()`, `onCallRejected()`, `onCallEnded()`

---

### **Công đoạn 7: Server Implementation**

**Files:**
- `src/main/java/org/example/danbainoso/server/ServerMain.java`
- `src/main/java/org/example/danbainoso/server/ChatServiceImpl.java`
- `src/main/java/org/example/danbainoso/server/VideoServiceImpl.java`

**Chức năng:**

1. **ServerMain.java**: Entry point của server
   - Khởi tạo database connection
   - Tạo RMI registry
   - Bind ChatService và VideoService vào registry
   - Quản lý shutdown hook

2. **ChatServiceImpl.java**: Implementation của ChatService
   - Implement tất cả methods từ ChatService interface
   - Quản lý client callbacks (ConcurrentHashMap)
   - Gửi notifications real-time khi có tin nhắn mới
   - Xử lý các operations: login, register, send message, create group...

3. **VideoServiceImpl.java**: Implementation của VideoService
   - Quản lý active calls (ConcurrentHashMap)
   - Xử lý call lifecycle: initiate → accept/reject → end
   - Gửi notifications cho cả caller và receiver

**Cách chạy server:**
```bash
# Compile project
mvn clean compile

# Run server
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
```

---

### **Công đoạn 8: Client Implementation**

**Files:**
- `src/main/java/org/example/danbainoso/client/ClientMain.java`
- `src/main/java/org/example/danbainoso/client/ClientRMI.java`
- `src/main/java/org/example/danbainoso/client/MediaHandler.java`

**Chức năng:**

1. **ClientMain.java**: Entry point của client (JavaFX Application)
   - Khởi tạo JavaFX application
   - Load login screen
   - Quản lý lifecycle (stop hook để update status)

2. **ClientRMI.java**: RMI client wrapper
   - Kết nối đến RMI registry
   - Lookup ChatService và VideoService
   - Register callbacks với server
   - Wrapper methods cho tất cả service calls

3. **MediaHandler.java**: Xử lý media files
   - Lưu/load media files
   - Play notification sounds
   - Quản lý media directory

---

### **Công đoạn 9: UI Controllers**

**Files:**
- `src/main/java/org/example/danbainoso/client/ui/LoginController.java`
- `src/main/java/org/example/danbainoso/client/ui/ChatController.java`
- `src/main/java/org/example/danbainoso/client/ui/GroupController.java`

**Chức năng:**

1. **LoginController.java**:
   - Xử lý đăng nhập (`handleLogin()`)
   - Xử lý đăng ký (`handleRegister()`)
   - Chuyển sang màn hình chat sau khi login thành công

2. **ChatController.java**:
   - Implement `ChatClientCallback` và `VideoClientCallback`
   - Load danh sách contacts và groups
   - Hiển thị tin nhắn (private và group)
   - Gửi tin nhắn (`sendMessage()`)
   - Xử lý incoming calls
   - Real-time updates qua callbacks

3. **GroupController.java**:
   - Tạo nhóm mới
   - Validate input
   - Gọi service để tạo group

---

### **Công đoạn 10: FXML UI Files**

**Files:**
- `src/main/resources/org/example/danbainoso/client/ui/login.fxml`
- `src/main/resources/org/example/danbainoso/client/ui/chat.fxml`
- `src/main/resources/org/example/danbainoso/client/ui/group.fxml`

**Chức năng:**
- Định nghĩa layout UI bằng FXML
- Bind với controllers qua `fx:controller`
- Định nghĩa các UI components (TextField, Button, ListView...)

**Cấu trúc:**
- **login.fxml**: Form đăng nhập/đăng ký
- **chat.fxml**: Màn hình chat chính (contacts list, groups list, message area)
- **group.fxml**: Dialog tạo nhóm mới

---

### **Công đoạn 11: CSS Styling**

**File:** `src/main/resources/css/style.css`

**Chức năng:**
- Styling cho tất cả UI components
- Color scheme: Green (#4CAF50), Blue (#2196F3), Orange (#FF9800)...
- Hover effects cho buttons
- Focus styles cho input fields
- Custom scrollbar styles

---

## 🚀 Hướng dẫn Setup và Chạy Project

### **Bước 1: Cài đặt MySQL**

1. Cài đặt MySQL Server
2. Tạo database:
```sql
CREATE DATABASE chat_app;
```

3. Import schema:
```bash
mysql -u root -p chat_app < src/main/resources/db/schema.sql
```

### **Bước 2: Cấu hình config.properties**

File `config.properties` ở root project:
```properties
db.url=jdbc:mysql://localhost:3306/chat_app?useSSL=false&serverTimezone=UTC
db.username=root
db.password=your_password
```

### **Bước 3: Build Project**

```bash
mvn clean install
```

### **Bước 4: Chạy Server**

```bash
# Terminal 1
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
```

### **Bước 5: Chạy Client**

```bash
# Terminal 2 (hoặc nhiều terminals để test nhiều clients)
mvn javafx:run
```

Hoặc:
```bash
mvn exec:java -Dexec.mainClass="org.example.danbainoso.client.ClientMain"
```

---

## 📋 Hướng dẫn Copy Files (nếu cần migrate)

### **Nếu bạn muốn copy project sang máy khác:**

1. **Copy toàn bộ folder `danbainoso/`**
2. **Đảm bảo có:**
   - Java 22+ installed
   - Maven installed
   - MySQL Server running
   - Database `chat_app` đã được tạo

3. **Cập nhật `config.properties`** với thông tin database mới

4. **Chạy lại:**
```bash
mvn clean install
mvn exec:java -Dexec.mainClass="org.example.danbainoso.server.ServerMain"
```

---

## 🔧 Chức năng cụ thể của từng component

### **1. Authentication System**
- **Login**: Xác thực username/password, hash password bằng BCrypt
- **Register**: Tạo user mới, tự động hash password
- **Status Management**: Cập nhật ONLINE/OFFLINE khi login/logout

### **2. Messaging System**
- **Private Messages**: Chat 1-1 giữa 2 users
- **Group Messages**: Chat trong nhóm nhiều users
- **Real-time Delivery**: Sử dụng RMI callbacks để push messages ngay lập tức
- **Message History**: Load lịch sử tin nhắn (50 messages mặc định)
- **Read Status**: Đánh dấu tin nhắn đã đọc

### **3. Group Management**
- **Create Group**: Tạo nhóm mới, creator tự động là ADMIN
- **Add/Remove Members**: Quản lý thành viên nhóm
- **Group List**: Hiển thị tất cả nhóm user tham gia
- **Member Count**: Hiển thị số lượng thành viên

### **4. Video Call System** (Basic implementation)
- **Initiate Call**: Bắt đầu cuộc gọi
- **Accept/Reject**: Chấp nhận/từ chối cuộc gọi
- **End Call**: Kết thúc cuộc gọi
- **Call Notifications**: Thông báo real-time qua callbacks

### **5. Real-time Updates**
- **User Status**: Cập nhật trạng thái user real-time
- **New Messages**: Push tin nhắn mới ngay lập tức
- **Group Events**: Thông báo khi có user join/leave group

---

## 🐛 Troubleshooting

### **Lỗi: Cannot connect to RMI registry**
- Đảm bảo server đã chạy trước khi start client
- Kiểm tra port 1099 không bị block bởi firewall

### **Lỗi: Database connection failed**
- Kiểm tra MySQL đang chạy
- Kiểm tra `config.properties` có đúng thông tin
- Kiểm tra database `chat_app` đã được tạo

### **Lỗi: JavaFX runtime not found**
- Đảm bảo JavaFX dependencies đã được download
- Chạy `mvn clean install` để download dependencies

---

## 📝 Notes

- **Password mặc định** cho sample users: `password` (đã được hash trong schema.sql)
- **RMI Port**: 1099 (có thể thay đổi trong config.properties)
- **Database**: MySQL 8.0+ recommended
- **Java Version**: Java 22 required

---

## 🎯 Tính năng có thể mở rộng

1. **File Sharing**: Upload/download files trong chat
2. **Image Messages**: Gửi hình ảnh
3. **Voice Messages**: Gửi voice notes
4. **Video Call UI**: Giao diện video call thực tế
5. **Friendship System**: Kết bạn, block user
6. **Message Search**: Tìm kiếm tin nhắn
7. **Message Reactions**: Like, react tin nhắn
8. **Group Admin Features**: Quyền admin trong nhóm

---

Chúc bạn code vui vẻ! 🚀

