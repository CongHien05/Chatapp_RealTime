# 🚀 Kế Hoạch Sprint - DanBaiNoSo Chat

## 📋 Tổng Quan

Dự án được chia thành **6 Sprint**, mỗi sprint tập trung vào một nhóm tính năng cụ thể, phù hợp cho đồ án môn học.

**Thời gian mỗi sprint**: 1-2 tuần  
**Tổng thời gian**: 8-10 tuần

---

# 📦 SPRINT 1: Cải Thiện Messaging & UX

## 🎯 Mục Tiêu
Hoàn thiện trải nghiệm chat cơ bản với các tính năng cần thiết.

## ✅ Tasks

### 1.1. Read Receipts (Đã đọc/Chưa đọc)
- [ ] Thêm field `isRead` vào bảng `messages` (nếu chưa có)
- [ ] Update `MessageDAO.java`: Method `markAsRead()`
- [ ] Update `ChatServiceImpl.java`: Tự động đánh dấu đã đọc khi user xem tin nhắn
- [ ] Update `ChatController.java`: Hiển thị icon "✓" (đã đọc) hoặc "✓✓" (chưa đọc)
- [ ] Test: Gửi tin nhắn và kiểm tra trạng thái đã đọc

**Files cần sửa:**
- `MessageDAO.java`
- `ChatServiceImpl.java`
- `ChatController.java`
- `Message.java` (model)

**Ước tính**: 1-2 ngày

---

### 1.2. Message Timestamps
- [ ] Cải thiện hiển thị thời gian trong `ChatController.java`
- [ ] Format: "HH:mm" cho tin nhắn hôm nay, "dd/MM HH:mm" cho tin nhắn cũ
- [ ] Hiển thị timestamp bên dưới mỗi tin nhắn
- [ ] Style timestamp nhỏ, màu xám

**Files cần sửa:**
- `ChatController.java`
- `chat.fxml` (nếu cần)
- `style.css`

**Ước tính**: 0.5-1 ngày

---

### 1.3. Edit/Delete Messages
- [ ] Thêm field `isDeleted` và `isEdited` vào bảng `messages`
- [ ] Update `MessageDAO.java`: Methods `updateMessage()`, `deleteMessage()`
- [ ] Update `ChatService.java`: Thêm methods `editMessage()`, `deleteMessage()`
- [ ] Update `ChatServiceImpl.java`: Implement edit/delete
- [ ] Update `ChatController.java`: 
  - Right-click menu: "Sửa", "Xóa"
  - Dialog sửa tin nhắn
  - Hiển thị "(đã chỉnh sửa)" cho tin nhắn đã sửa
- [ ] Test: Sửa và xóa tin nhắn

**Files cần sửa:**
- `MessageDAO.java`
- `ChatService.java`
- `ChatServiceImpl.java`
- `ChatController.java`
- `Message.java` (model)

**Ước tính**: 2-3 ngày

---

### 1.4. Message Pagination (Load More)
- [ ] Update `MessageDAO.java`: Method `getMessages()` với pagination (LIMIT, OFFSET)
- [ ] Update `ChatController.java`: 
  - Load 20 tin nhắn đầu tiên
  - Button "Tải thêm" ở đầu danh sách
  - Load thêm 20 tin nhắn khi click
- [ ] Test: Load nhiều tin nhắn

**Files cần sửa:**
- `MessageDAO.java`
- `ChatController.java`

**Ước tính**: 1-2 ngày

---

## 📦 Deliverables
- Read receipts hoạt động
- Timestamps hiển thị rõ ràng
- Có thể sửa/xóa tin nhắn
- Load more messages

## ⏱️ Tổng thời gian: 5-8 ngày

---

# 📦 SPRINT 2: Friendship System

## 🎯 Mục Tiêu
Xây dựng hệ thống kết bạn hoàn chỉnh với UI đầy đủ.

## ✅ Tasks

### 2.1. Friendship DAO
- [ ] Tạo `FriendshipDAO.java`:
  - `sendFriendRequest(userId1, userId2)`
  - `acceptFriendRequest(friendshipId)`
  - `rejectFriendRequest(friendshipId)`
  - `getFriendRequests(userId)`
  - `getFriends(userId)`
  - `removeFriend(userId1, userId2)`
  - `blockUser(userId1, userId2)`
- [ ] Test DAO methods

**Files cần tạo:**
- `FriendshipDAO.java`

**Ước tính**: 1-2 ngày

---

### 2.2. Friendship Service
- [ ] Update `ChatService.java`: Thêm methods cho friendship
- [ ] Update `ChatServiceImpl.java`: Implement friendship methods
- [ ] Notify users khi có friend request mới

**Files cần sửa:**
- `ChatService.java`
- `ChatServiceImpl.java`

**Ước tính**: 1-2 ngày

---

### 2.3. Friendship UI
- [ x] Tạo `FriendshipController.java`:
  - Hiển thị danh sách bạn bè
  - Hiển thị friend requests (pending)
  - Button "Kết bạn" trong chat
  - Accept/Reject friend request
- [x ] Tạo `friendship.fxml`: Dialog quản lý bạn bè
- [x ] Update `ChatController.java`: 
  - Button "Kết bạn" khi chat với user chưa là bạn
  - Hiển thị trạng thái bạn bè
- [x ] Test: Gửi/chấp nhận/từ chối friend request

**Files cần tạo:**
- `FriendshipController.java`
- `friendship.fxml`

**Files cần sửa:**
- `ChatController.java`

**Ước tính**: 2-3 ngày

---

### 2.4. Block User
- [x] Update `FriendshipDAO.java`: Method `blockUser()`
- [x] Update `ChatServiceImpl.java`: Block user logic
- [x] Update `ChatController.java`: 
  - Button "Chặn" trong menu
  - Ẩn tin nhắn từ user bị chặn
- [x] Test: Block và unblock user

**Files cần sửa:**
- `FriendshipDAO.java`
- `ChatServiceImpl.java`
- `ChatController.java`

**Ước tính**: 1 ngày

---

## 📦 Deliverables
- Friendship DAO hoàn chỉnh
- UI kết bạn/chấp nhận/từ chối
- Block user
- Danh sách bạn bè

## ⏱️ Tổng thời gian: 5-8 ngày

---

# 📦 SPRINT 3: Group Management Nâng Cao

## 🎯 Mục Tiêu
Hoàn thiện quản lý nhóm với các tính năng admin và cài đặt.

## ✅ Tasks

### 3.1. Group Admin & Permissions
- [x] Thêm field `role` vào bảng `group_members` (ADMIN, MEMBER)
- [x] Update `GroupDAO.java`: 
  - `setGroupAdmin(groupId, userId)`
  - `getGroupAdmins(groupId)`
  - `checkUserRole(groupId, userId)`
- [x] Update `Group.java` (model): Thêm role enum
- [x] Test: Phân quyền admin

**Files cần sửa:**
- `GroupDAO.java`
- `Group.java`
- `schema.sql` (migration)

**Ước tính**: 1-2 ngày

---

### 3.2. Group Settings UI
- [x] Tạo `GroupSettingsController.java`:
  - Hiển thị thông tin nhóm
  - Chỉnh sửa tên nhóm, mô tả
  - Chỉ admin mới có thể chỉnh sửa
- [x] Tạo `group_settings.fxml`: Dialog cài đặt nhóm
- [x] Update `ChatController.java`: 
  - Button "Cài đặt nhóm" (chỉ admin thấy)
  - Mở dialog settings
- [x] Test: Chỉnh sửa thông tin nhóm

**Files cần tạo:**
- `GroupSettingsController.java`
- `group_settings.fxml`

**Files cần sửa:**
- `ChatController.java`
- `GroupDAO.java`

**Ước tính**: 2 ngày

---

### 3.3. Remove Members UI
- [x] Update `GroupDAO.java`: Method `removeMember()` (đã có, kiểm tra lại)
- [x] Update `ChatServiceImpl.java`: Method `removeMemberFromGroup()`
- [x] Update `ChatController.java`:
  - Right-click menu trong danh sách thành viên: "Xóa khỏi nhóm"
  - Chỉ admin mới có thể xóa
  - Confirm dialog trước khi xóa
- [ ] Test: Xóa thành viên khỏi nhóm

**Files cần sửa:**
- `ChatController.java`
- `ChatServiceImpl.java`
- `GroupDAO.java`

**Ước tính**: 1-2 ngày

---

### 3.4. Add Members UI
- [x] Update `ChatController.java`:
  - Button "Thêm thành viên" trong group settings
  - Dialog chọn user từ danh sách bạn bè
  - Thêm user vào nhóm
- [x] Test: Thêm thành viên mới

**Files cần sửa:**
- `ChatController.java`
- `GroupController.java` (hoặc tạo dialog mới)

**Ước tính**: 1 ngày

---

## 📦 Deliverables
- Group admin roles
- Group settings UI
- Add/Remove members
- Permissions check

## ⏱️ Tổng thời gian: 5-7 ngày

---

# 📦 SPRINT 4: Media Messages (Images & Files)

## 🎯 Mục Tiêu
Thêm khả năng gửi ảnh và file trong chat.

## ✅ Tasks

### 4.1. File Storage Setup
- [ ] Tạo thư mục `uploads/` trong project
- [ ] Update `Message.java`: Thêm field `filePath`, `fileType`, `fileName`
- [ ] Update bảng `messages`: Thêm columns `file_path`, `file_type`, `file_name`
- [ ] Update `MessageDAO.java`: Lưu thông tin file

**Files cần sửa:**
- `Message.java`
- `MessageDAO.java`
- `schema.sql`

**Ước tính**: 0.5-1 ngày

---

### 4.2. Image Upload & Display
- [ ] Update `ChatController.java`:
  - Button "Chọn ảnh" hoặc icon 📷
  - FileChooser để chọn ảnh
  - Upload ảnh lên server (copy vào `uploads/`)
  - Gửi message với `filePath`
- [ ] Update `ChatController.java`:
  - Hiển thị ảnh trong message area
  - ImageView với kích thước phù hợp (max width: 300px)
  - Click để xem ảnh full size
- [ ] Test: Gửi và hiển thị ảnh

**Files cần sửa:**
- `ChatController.java`
- `ChatServiceImpl.java` (xử lý upload)

**Ước tính**: 2-3 ngày

---

### 4.3. File Upload & Download
- [ ] Update `ChatController.java`:
  - Button "Đính kèm file" hoặc icon 📎
  - FileChooser để chọn file
  - Upload file lên server
  - Hiển thị file info (tên, kích thước)
- [ ] Update `ChatController.java`:
  - Hiển thị file trong message với icon
  - Button "Tải xuống" để download
- [ ] Update `MediaHandler.java`: Methods `saveFile()`, `loadFile()`
- [ ] Test: Upload và download file

**Files cần sửa:**
- `ChatController.java`
- `MediaHandler.java`
- `ChatServiceImpl.java`

**Ước tính**: 2-3 ngày

---

### 4.4. File Size Limit & Validation
- [ ] Thêm validation: Max file size 10MB
- [ ] Hiển thị lỗi nếu file quá lớn
- [ ] Validate file types (chỉ cho phép một số loại file)
- [ ] Test: Upload file lớn, file không hợp lệ

**Files cần sửa:**
- `ChatController.java`
- `ChatServiceImpl.java`

**Ước tính**: 0.5-1 ngày

---

## 📦 Deliverables
- Upload và hiển thị ảnh
- Upload và download file
- File validation
- File storage

## ⏱️ Tổng thời gian: 5-8 ngày

---

# 📦 SPRINT 5: User Profile & Notifications

## 🎯 Mục Tiêu
Thêm profile user và hệ thống thông báo.

## ✅ Tasks

### 5.1. User Profile
- [ ] Update bảng `users`: Thêm columns `avatar_path`, `status_message`, `bio`
- [ ] Update `UserDAO.java`: Methods `updateProfile()`, `updateAvatar()`
- [ ] Update `User.java` (model): Thêm fields mới
- [ ] Test: Update profile

**Files cần sửa:**
- `UserDAO.java`
- `User.java`
- `schema.sql`

**Ước tính**: 1 ngày

---

### 5.2. Profile UI
- [ ] Tạo `ProfileController.java`:
  - Hiển thị thông tin user
  - Edit profile (tên, email, bio, status message)
  - Upload avatar
- [ ] Tạo `profile.fxml`: Dialog profile
- [ ] Update `ChatController.java`:
  - Click vào avatar/username → mở profile
  - Hiển thị avatar trong chat
- [ ] Test: Edit profile, upload avatar

**Files cần tạo:**
- `ProfileController.java`
- `profile.fxml`

**Files cần sửa:**
- `ChatController.java`
- `ChatServiceImpl.java`

**Ước tính**: 2-3 ngày

---

### 5.3. Desktop Notifications
- [ ] Update `ChatController.java`:
  - Hiển thị notification khi có tin nhắn mới (nếu app không focus)
  - JavaFX `Notification` hoặc `TrayIcon`
- [ ] Test: Notification khi có tin nhắn mới

**Files cần sửa:**
- `ChatController.java`

**Ước tính**: 1 ngày

---

### 5.4. Sound Notifications
- [ ] Update `MediaHandler.java`: Method `playNotificationSound()`
- [ ] Update `ChatController.java`:
  - Phát âm thanh khi có tin nhắn mới
  - Toggle sound on/off trong settings
- [ ] Thêm file âm thanh vào `resources/sounds/`
- [ ] Test: Âm thanh thông báo

**Files cần sửa:**
- `MediaHandler.java`
- `ChatController.java`

**Ước tính**: 1 ngày

---

### 5.5. Notification Settings
- [ ] Tạo `SettingsController.java`:
  - Toggle desktop notifications
  - Toggle sound notifications
  - Lưu preferences
- [ ] Tạo `settings.fxml`: Dialog cài đặt
- [ ] Update `ChatController.java`: Button "Cài đặt"
- [ ] Test: Bật/tắt notifications

**Files cần tạo:**
- `SettingsController.java`
- `settings.fxml`

**Files cần sửa:**
- `ChatController.java`

**Ước tính**: 1-2 ngày

---

## 📦 Deliverables
- User profile với avatar
- Desktop notifications
- Sound notifications
- Settings UI

## ⏱️ Tổng thời gian: 6-9 ngày

---

# 📦 SPRINT 6: Search & Polish

## 🎯 Mục Tiêu
Thêm tính năng tìm kiếm và hoàn thiện ứng dụng.

## ✅ Tasks

### 6.1. Search Users
- [ ] Update `UserDAO.java`: Method `searchUsers(keyword)`
- [ ] Update `ChatService.java`: Method `searchUsers()`
- [ ] Update `ChatServiceImpl.java`: Implement search
- [ ] Update `ChatController.java`:
  - Search box trong contacts list
  - Filter contacts theo keyword
- [ ] Test: Tìm kiếm users

**Files cần sửa:**
- `UserDAO.java`
- `ChatService.java`
- `ChatServiceImpl.java`
- `ChatController.java`

**Ước tính**: 1-2 ngày

---

### 6.2. Search Messages
- [ ] Update `MessageDAO.java`: Method `searchMessages(userId, keyword)`
- [ ] Update `ChatService.java`: Method `searchMessages()`
- [ ] Update `ChatServiceImpl.java`: Implement search
- [ ] Update `ChatController.java`:
  - Search box trong message area
  - Highlight kết quả tìm kiếm
  - Navigate giữa các kết quả
- [ ] Test: Tìm kiếm trong tin nhắn

**Files cần sửa:**
- `MessageDAO.java`
- `ChatService.java`
- `ChatServiceImpl.java`
- `ChatController.java`

**Ước tính**: 2-3 ngày

---

### 6.3. Connection Retry Logic
- [ ] Update `ClientRMI.java`:
  - Tự động reconnect nếu mất kết nối
  - Retry logic với exponential backoff
  - Hiển thị trạng thái kết nối
- [ ] Test: Disconnect và reconnect

**Files cần sửa:**
- `ClientRMI.java`
- `ChatController.java` (hiển thị status)

**Ước tính**: 1-2 ngày

---

### 6.4. Error Handling & Validation
- [ ] Cải thiện error handling trong tất cả controllers
- [ ] Validation input trong tất cả forms
- [ ] Hiển thị error messages rõ ràng
- [ ] Test: Các trường hợp lỗi

**Files cần sửa:**
- Tất cả controllers
- Service implementations

**Ước tính**: 1-2 ngày

---

### 6.5. UI/UX Improvements
- [ ] Cải thiện CSS styling
- [ ] Thêm loading indicators
- [ ] Thêm tooltips
- [ ] Responsive layout
- [ ] Test: UI/UX tổng thể

**Files cần sửa:**
- `style.css`
- Tất cả FXML files
- Controllers

**Ước tính**: 1-2 ngày

---

### 6.6. Testing & Bug Fixes
- [ ] Test tất cả tính năng
- [ ] Fix bugs phát hiện
- [ ] Code review
- [ ] Documentation

**Ước tính**: 2-3 ngày

---

## 📦 Deliverables
- Search users và messages
- Connection retry
- Error handling tốt hơn
- UI/UX cải thiện
- Bug fixes

## ⏱️ Tổng thời gian: 8-12 ngày

---

# 📊 Tổng Kết

## 📈 Timeline

| Sprint | Thời gian | Tổng |
|--------|-----------|------|
| Sprint 1 | 5-8 ngày | 5-8 ngày |
| Sprint 2 | 5-8 ngày | 10-16 ngày |
| Sprint 3 | 5-7 ngày | 15-23 ngày |
| Sprint 4 | 5-8 ngày | 20-31 ngày |
| Sprint 5 | 6-9 ngày | 26-40 ngày |
| Sprint 6 | 8-12 ngày | 34-52 ngày |

**Tổng thời gian**: 34-52 ngày (khoảng 7-10 tuần)

---

## 🎯 Mục Tiêu Tổng Thể

Sau khi hoàn thành 6 sprint, ứng dụng sẽ có:

✅ **Messaging hoàn chỉnh**: Read receipts, timestamps, edit/delete, pagination  
✅ **Friendship system**: Kết bạn, chấp nhận/từ chối, block  
✅ **Group management**: Admin roles, settings, add/remove members  
✅ **Media messages**: Images, files  
✅ **User profile**: Avatar, status message, edit profile  
✅ **Notifications**: Desktop & sound  
✅ **Search**: Users & messages  
✅ **Polish**: Error handling, UI/UX, testing  

---

## 📝 Lưu Ý

- Mỗi sprint có thể copy riêng để gửi cho AI assistant
- Có thể điều chỉnh thứ tự sprint tùy theo ưu tiên
- Một số tính năng có thể bỏ qua nếu không đủ thời gian
- Ưu tiên: Sprint 1, 2, 3 (core features)

---

**Version**: 1.0  
**Cập nhật**: Hôm nay

