## Tài liệu trình bày sản phẩm – DanBaiNoSo Chat (bản dùng để vấn đáp)

Tài liệu này chỉ mô tả **những chức năng đã sử dụng được** trong project, kèm theo **code minh họa** để bạn có thể chỉ vào file/method khi bảo vệ:

- Đăng ký / đăng nhập & mã hóa mật khẩu.
- Chat 1-1.
- Chat nhóm.
- Hệ thống bạn bè (kết bạn, chặn).
- Quản lý trạng thái online/offline.
- Kết nối RMI, chia sẻ qua LAN.
- Tầng database & an toàn dữ liệu.

Các chức năng **video call / streaming** hiện chưa hoàn thiện nên **bỏ qua khi trình bày**.

---

### 1. Kiến trúc tổng quát (có liên quan tới các chức năng đang chạy)

- **Client** – `org.example.danbainoso.client`
  - `ClientMain`: khởi động JavaFX, load giao diện login.
  - `ClientRMI`: kết nối RMI, gọi các hàm trong `ChatService`, đăng ký callback.
  - `ui.*Controller`: điều khiển UI & gọi `ClientRMI`:
    - `LoginController`: đăng nhập, đăng ký.
    - `ChatController`: chat 1-1, chat nhóm, bạn bè, block, trạng thái.

- **Server** – `org.example.danbainoso.server`
  - `ServerMain`: khởi động server, kết nối DB, bind RMI service.
  - `ChatServiceImpl`: triển khai nghiệp vụ cho `ChatService`.

- **Shared (RMI + models)** – `org.example.danbainoso.shared`
  - `ChatService`: interface RMI, client gọi từ xa.
  - `ChatClientCallback`: interface callback, server gọi ngược về client để push real-time.
  - `models.*`: `User`, `Message`, `Group`, `Friendship`, `BlockStatus`…

- **Database** – `org.example.danbainoso.database`
  - `DatabaseConnection`: quản lý connection pool bằng HikariCP.
  - `UserDAO`, `MessageDAO`, `GroupDAO`, `FriendshipDAO`: thao tác DB.

- **Config & Utils** – `org.example.danbainoso.utils`
  - `Config`: đọc `config.properties` (DB, RMI host/port).
  - `EncryptionUtil`: hash & verify mật khẩu bằng BCrypt.

---

### 2. Kết nối RMI & chia sẻ qua LAN

#### 2.1 Phía server – `ServerMain`

- Trách nhiệm:
  - Kiểm tra DB.
  - Tạo `ChatServiceImpl`.
  - Tạo RMI Registry và bind service với tên `"ChatService"`.
  - Thiết lập `java.rmi.server.hostname` để **chia sẻ qua mạng LAN**.

Code chính:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ServerMain.java
// Tạo service và registry, bind lên RMI
ChatService chatService = new ChatServiceImpl();

String host = Config.getServerHost();
int port = Config.getServerRmiPort();

// Cho phép client trong LAN kết nối bằng IP server
System.setProperty("java.rmi.server.hostname", host);

Registry registry;
try {
    registry = LocateRegistry.getRegistry(host, port);
    registry.list(); // test registry có tồn tại không
} catch (Exception e) {
    registry = LocateRegistry.createRegistry(port);
}

registry.rebind("ChatService", chatService);
```

**Ý nghĩa khi thuyết trình**:

- Server là nơi **đăng ký** các service vào RMI Registry.
- `server.host` trong `config.properties`:
  - Nếu để `localhost` → chạy local.
  - Nếu để `192.168.x.x` → client máy khác trong LAN có thể kết nối.

#### 2.2 Phía client – `ClientRMI.connect()`

Code chính:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ClientRMI.java
public boolean connect() {
    try {
        String host = Config.getClientRmiRegistry();
        int port = Config.getClientRmiPort();

        Registry registry = LocateRegistry.getRegistry(host, port);
        chatService = (ChatService) registry.lookup("ChatService");

        logger.info("Connected to RMI registry at {}:{}", host, port);
        return true;
    } catch (RemoteException | NotBoundException e) {
        logger.error("Failed to connect to RMI registry", e);
        return false;
    }
}
```

**Cấu hình LAN** – `config.properties`:

```startLine:endLine:config.properties
# Server Configuration
server.host=192.168.1.6
server.rmi.port=1099

# Client Configuration
client.rmi.registry=192.168.1.6
client.rmi.port=1099
```

Khi bảo vệ, bạn có thể giải thích: *“Client dùng IP 192.168.1.6 và port 1099 để lookup `ChatService` trên server qua RMI, tương tự như BankingRMI.”*

---

### 3. Đăng ký & đăng nhập (có mã hóa mật khẩu)

#### 3.1 Đăng ký – flow & code

**Giao diện** – `LoginController.handleRegister()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/LoginController.java
@FXML
private void handleRegister() {
    String username = usernameField.getText().trim();
    String password = passwordField.getText();
    // ... validate ...
    User newUser = new User(username, password, username + "@example.com", username);
    boolean success = clientRMI.register(newUser);
    // ... thông báo thành công / thất bại ...
}
```

**Trên server** – `ChatServiceImpl.register()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public boolean register(User user) throws RemoteException {
    try {
        // kiểm tra trùng username
        User existingUser = userDAO.getUserByUsername(user.getUsername());
        if (existingUser != null) {
            return false;
        }
        // kiểm tra trùng email
        existingUser = userDAO.getUserByEmail(user.getEmail());
        if (existingUser != null) {
            return false;
        }
        userDAO.createUser(user); // tạo user, trong đó có hash mật khẩu
        return true;
    } catch (SQLException e) {
        throw new RemoteException("Registration failed: " + e.getMessage(), e);
    }
}
```

**Hash mật khẩu** – `UserDAO.createUser()`:

```startLine:endLine:src/main/java/org/example/danbainoso/database/UserDAO.java
public User createUser(User user) throws SQLException {
    String sql = "INSERT INTO users (username, password, email, full_name, status) VALUES (?, ?, ?, ?, ?)";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        String hashedPassword = EncryptionUtil.hashPassword(user.getPassword());
        pstmt.setString(1, user.getUsername());
        pstmt.setString(2, hashedPassword); // lưu hash, không lưu plain text
        // ...
        pstmt.executeUpdate();
        // ...
        return user;
    }
}
```

**Khi trình bày**, bạn có thể nói:

> “Khi đăng ký, client gửi User lên server. Server kiểm tra trùng username/email, sau đó hash mật khẩu bằng BCrypt (`EncryptionUtil.hashPassword`) trước khi lưu vào DB. Không bao giờ lưu mật khẩu rõ.”

#### 3.2 Đăng nhập – flow & code

**Giao diện** – `LoginController.handleLogin()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/LoginController.java
@FXML
private void handleLogin() {
    String username = usernameField.getText().trim();
    String password = passwordField.getText();
    User user = clientRMI.login(username, password);
    if (user != null) {
        openChatWindow(user); // mở màn hình chat
    } else {
        // báo lỗi đăng nhập
    }
}
```

**Kiểm tra mật khẩu trên server** – `UserDAO.authenticate()`:

```startLine:endLine:src/main/java/org/example/danbainoso/database/UserDAO.java
public User authenticate(String username, String password) throws SQLException {
    User user = getUserByUsername(username);
    if (user != null && EncryptionUtil.verifyPassword(password, user.getPassword())) {
        return user;
    }
    return null;
}
```

**Cập nhật trạng thái ONLINE** – `ChatServiceImpl.login()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public User login(String username, String password) throws RemoteException {
    try {
        User user = userDAO.authenticate(username, password);
        if (user != null) {
            userDAO.updateUserStatus(user.getUserId(), User.UserStatus.ONLINE);
        }
        return user;
    } catch (SQLException e) {
        throw new RemoteException("Login failed: " + e.getMessage(), e);
    }
}
```

---

### 4. Chat 1-1 (private chat)

#### 4.1 Chọn người để chat & load lịch sử

**Load danh sách contact** – `ChatController.loadContacts()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void loadContacts() {
    if (currentUser == null) return;
    new Thread(() -> {
        try {
            List<User> users = clientRMI.searchUsers("");
            Platform.runLater(() -> {
                cachedContacts.clear();
                for (User user : users) {
                    if (user.getUserId() != currentUser.getUserId()) {
                        cachedContacts.add(user);
                    }
                }
                // đổ ra ListView
            });
        } catch (Exception e) {
            logger.error("Failed to load contacts", e);
        }
    }).start();
}
```

**Chọn contact & chuẩn bị cuộc trò chuyện**:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void loadContactChat(User user) {
    selectedContact = user;
    selectedGroup = null;
    currentChatLabel.setText("Chat với: " + user.getUsername());
    updateStatusLabel(user);          // cập nhật label online/offline
    checkFriendshipStatus(user);      // hiển thị / ẩn nút kết bạn
    prepareContactConversation(user); // kiểm tra block + load tin nhắn
}
```

#### 4.2 Gửi tin nhắn & nhận real-time

**Gửi tin** – `ChatController.sendMessage()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
@FXML
private void sendMessage() {
    String content = messageField.getText().trim();
    if (content.isEmpty()) return;
    if (selectedContact == null && selectedGroup == null) return;

    // nếu đang bị block thì không cho gửi
    if (selectedContact != null && currentBlockStatus != BlockStatus.NONE) {
        // ... showAlert ...
        return;
    }

    try {
        Message message = new Message();
        message.setSenderId(currentUser.getUserId());
        message.setContent(content);
        message.setMessageType(Message.MessageType.TEXT);
        if (selectedContact != null) {
            message.setReceiverId(selectedContact.getUserId());
        }
        clientRMI.sendMessage(message);
        messageField.clear();
    } catch (Exception e) {
        logger.error("Failed to send message", e);
    }
}
```

**Xử lý gửi trên server** – `ChatServiceImpl.sendMessage()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public Message sendMessage(Message message) throws RemoteException {
    try {
        // kiểm tra block nếu là chat 1-1
        if (message.getReceiverId() != null) {
            BlockStatus status = friendshipDAO.getBlockStatus(
                    message.getSenderId(), message.getReceiverId());
            if (status == BlockStatus.BLOCKED_BY_OTHER) {
                throw new RemoteException("Bạn đã bị chặn bởi người này.");
            } else if (status == BlockStatus.BLOCKED_BY_ME) {
                throw new RemoteException("Bạn đã chặn người này...");
            }
        }

        Message savedMessage = messageDAO.createMessage(message); // lưu DB
        enrichSenderMetadata(savedMessage); // gắn thêm tên, avatar

        if (message.isGroupMessage()) {
            notifyGroupMessage(savedMessage);
        } else {
            notifyPrivateMessage(savedMessage); // push cho 2 client
        }
        return savedMessage;
    } catch (SQLException e) {
        throw new RemoteException("Failed to send message: " + e.getMessage(), e);
    }
}
```

**Push real-time cho client** – `notifyPrivateMessage`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
private void notifyPrivateMessage(Message message) {
    String receiverKey = "user_" + message.getReceiverId();
    String senderKey = "user_" + message.getSenderId();

    ChatClientCallback receiver = clients.get(receiverKey);
    if (receiver != null) {
        receiver.onMessageReceived(message);
    }

    ChatClientCallback sender = clients.get(senderKey);
    if (sender != null) {
        sender.onMessageReceived(message); // confirm gửi thành công
    }
}
```

**Client nhận callback** – `ChatController.onMessageReceived()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
@Override
public void onMessageReceived(Message message) throws RemoteException {
    mediaHandler.playNotificationSound();          // phát âm thanh
    conversationLoader.handleIncomingMessage(message); // thêm vào UI nếu thuộc cuộc trò chuyện hiện tại
}
```

Khi trình bày, bạn có thể mô tả: *“Client gửi Message qua RMI → Server lưu DB → Server gọi callback `onMessageReceived` về cả phía sender và receiver để cập nhật UI ngay lập tức, không cần polling.”*

---

### 5. Chat nhóm (group chat)

#### 5.1 Tạo nhóm

**Mở cửa sổ tạo nhóm** – `ChatController.openCreateGroupWindow()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void openCreateGroupWindow() {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/.../group.fxml"));
    Parent root = loader.load();
    GroupController groupController = loader.getController();
    groupController.setCurrentUser(currentUser);
    groupController.setOnGroupCreated(
        createdGroup -> loadGroups(() -> selectGroupById(createdGroup.getGroupId()))
    );
    // show Stage...
}
```

**Trên server** – `ChatServiceImpl.createGroup()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public Group createGroup(Group group) throws RemoteException {
    try {
        Group createdGroup = groupDAO.createGroup(group);
        logger.info("Group created: {}", createdGroup.getGroupName());
        return createdGroup;
    } catch (SQLException e) {
        throw new RemoteException("Failed to create group: " + e.getMessage(), e);
    }
}
```

#### 5.2 Gửi tin nhắn trong nhóm

- Gửi tin: giống chat 1-1 nhưng `message.setGroupId(groupId)` thay vì `receiverId`.
- Server phân biệt bằng `message.isGroupMessage()` và gọi `notifyGroupMessage`.

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
private void notifyGroupMessage(Message message) {
    try {
        List<User> members = groupDAO.getGroupMembers(message.getGroupId());
        for (User member : members) {
            String memberKey = "user_" + member.getUserId();
            ChatClientCallback callback = clients.get(memberKey);
            if (callback != null) {
                callback.onMessageReceived(message);
            }
        }
    } catch (SQLException e) {
        logger.error("Failed to get group members for notification", e);
    }
}
```

Như vậy, **một lần gửi tin nhóm** sẽ được push đến **tất cả thành viên đang online** trong nhóm thông qua callback.

---

### 6. Hệ thống bạn bè & chặn (friendship + block)

#### 6.1 Gửi lời mời kết bạn

**Client** – `ChatController.sendFriendRequest()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void sendFriendRequest() {
    if (selectedContact == null || currentUser == null) return;
    new Thread(() -> {
        boolean success = clientRMI.sendFriendRequest(selectedContact.getUserId());
        Platform.runLater(() -> {
            if (success) showAlert("Đã gửi lời mời kết bạn...");
            else showAlert("Không thể gửi lời mời kết bạn.");
        });
    }).start();
}
```

**Server** – `ChatServiceImpl.sendFriendRequest()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public boolean sendFriendRequest(int requesterId, int targetId) throws RemoteException {
    if (requesterId == targetId) return false;
    try {
        Friendship friendship = friendshipDAO.sendFriendRequest(requesterId, targetId);
        if (friendship != null) {
            notifyFriendRequestReceived(friendship);
            return true;
        }
        return false;
    } catch (SQLException e) {
        if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
            return false;
        }
        throw new RemoteException("Failed to send friend request: " + e.getMessage(), e);
    }
}
```

#### 6.2 Chặn / bỏ chặn

**Kiểm tra trạng thái block khi mở chat** – `fetchBlockStatus()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void fetchBlockStatus(User contact) {
    new Thread(() -> {
        BlockStatus status = clientRMI.getBlockStatus(contact.getUserId());
        Platform.runLater(() -> {
            currentBlockStatus = status;
            updateBlockButtonState();
            if (status == BlockStatus.NONE) {
                enableMessaging();
                conversationLoader.loadConversation(selectedContact, null);
            } else {
                showBlockedState(status); // hiển thị message “bạn đã chặn/bị chặn”
            }
        });
    }).start();
}
```

**Toggle chặn/bỏ chặn** – `toggleBlockStatus()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void toggleBlockStatus() {
    if (selectedContact == null) return;
    final int targetId = selectedContact.getUserId();
    new Thread(() -> {
        boolean success;
        if (currentBlockStatus == BlockStatus.BLOCKED_BY_ME) {
            success = clientRMI.unblockUser(targetId);
        } else {
            success = clientRMI.blockUser(targetId);
        }
        Platform.runLater(() -> {
            if (!success) { showAlert("Không thể cập nhật trạng thái chặn."); return; }
            currentBlockStatus = (currentBlockStatus == BlockStatus.BLOCKED_BY_ME)
                    ? BlockStatus.NONE : BlockStatus.BLOCKED_BY_ME;
            updateBlockButtonState();
            // ... cập nhật UI và conversation ...
        });
    }).start();
}
```

---

### 7. Quản lý trạng thái online/offline

#### 7.1 Khi login và mở cửa sổ chat

**Client** – `ChatController.setCurrentUser()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
public void setCurrentUser(User user) {
    this.currentUser = user;
    clientRMI.setCurrentUser(user);
    try {
        clientRMI.updateUserStatus(User.UserStatus.ONLINE);
    } catch (Exception e) {
        logger.error("Failed to update user status", e);
    }
    setupWindowCloseHandler(); // khi đóng, set OFFLINE
    loadContacts();
    loadGroups();
}
```

**Server** – `ChatServiceImpl.updateUserStatus()`:

```startLine:endLine:src/main/java/org/example/danbainoso/server/ChatServiceImpl.java
@Override
public boolean updateUserStatus(int userId, User.UserStatus status) throws RemoteException {
    try {
        boolean updated = userDAO.updateUserStatus(userId, status);
        if (updated) {
            notifyUserStatusChanged(userId, status); // broadcast tới các client
        }
        return updated;
    } catch (SQLException e) {
        throw new RemoteException("Failed to update status: " + e.getMessage(), e);
    }
}
```

**Callback trên client** – `onUserStatusChanged()`:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
@Override
public void onUserStatusChanged(int userId, User.UserStatus status) throws RemoteException {
    Platform.runLater(() -> {
        loadContacts(); // reload danh sách để cập nhật chấm online/offline
        if (selectedContact != null && selectedContact.getUserId() == userId) {
            selectedContact.setStatus(status);
            updateStatusLabel(selectedContact);
        }
    });
}
```

#### 7.2 Khi logout / đóng cửa sổ

**Ví dụ – `handleLogout()`**:

```startLine:endLine:src/main/java/org/example/danbainoso/client/ui/ChatController.java
private void handleLogout() {
    // ... hỏi xác nhận ...
    clientRMI.updateUserStatus(User.UserStatus.OFFLINE);
    Thread.sleep(200);           // cho server kịp broadcast
    clientRMI.unregisterCallbacks();
    // load lại màn hình login.fxml
}
```

---

### 8. Tầng database & an toàn dữ liệu

#### 8.1 Connection pool – `DatabaseConnection`

```startLine:endLine:src/main/java/org/example/danbainoso/database/DatabaseConnection.java
private static void initializeDataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(Config.getDbUrl());
    config.setUsername(Config.getDbUsername());
    config.setPassword(Config.getDbPassword());
    config.setDriverClassName(Config.getDbDriver());
    config.setMaximumPoolSize(Config.getDbPoolSize());
    // ... timeout, leak detection ...
    dataSource = new HikariDataSource(config);
}
```

#### 8.2 Truy vấn dữ liệu an toàn – ví dụ `MessageDAO.createMessage`

```startLine:endLine:src/main/java/org/example/danbainoso/database/MessageDAO.java
public Message createMessage(Message message) throws SQLException {
    String sql = "INSERT INTO messages (sender_id, receiver_id, group_id, content, message_type, file_url, is_read, is_edited, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        pstmt.setInt(1, message.getSenderId());
        // ... set các field còn lại ...
        pstmt.executeUpdate();
        // ... lấy message_id, created_at ...
        return message;
    }
}
```

Khi vấn đáp, bạn có thể kết luận:

> “Tầng DAO luôn sử dụng `PreparedStatement` để chống SQL injection, kết nối được quản lý bởi HikariCP để tối ưu hiệu năng và độ ổn định.”

---

### 9. Gợi ý cách trình bày miệng (theo thứ tự dễ nhớ)

1. **Giới thiệu sản phẩm**: ứng dụng chat real-time dùng JavaFX + RMI + MySQL, hỗ trợ chat 1-1, chat nhóm, bạn bè, block, trạng thái online/offline.
2. **Kiến trúc**: 4 lớp – Client, Server, Shared (RMI + models), Database – mỗi lớp 1–2 câu, chỉ vào package tương ứng.
3. **Flow chính** (trình bày có code hỗ trợ):
   - Đăng ký & đăng nhập (hash mật khẩu, xác thực, set ONLINE).
   - Chat 1-1 (Client gửi Message → Server lưu DB → callback về 2 phía).
   - Chat nhóm (gửi 1 lần, server push cho tất cả thành viên nhóm).
   - Bạn bè & block (gửi request kết bạn, chặn/bỏ chặn, ẩn nội dung khi bị chặn).
   - Trạng thái online/offline & callback `onUserStatusChanged`.
4. **Kết nối LAN**: giải thích `server.host`, `client.rmi.registry`, `java.rmi.server.hostname` và chỉ ra `config.properties`.

Bạn chỉ cần mở file này + các class tương ứng trong IDE là có thể vừa nói vừa show code minh họa.  


