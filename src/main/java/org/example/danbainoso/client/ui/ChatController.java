package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.danbainoso.client.ClientMain;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.client.MediaHandler;
import org.example.danbainoso.shared.ChatClientCallback;
import org.example.danbainoso.shared.VideoClientCallback;
import org.example.danbainoso.shared.models.BlockStatus;
import org.example.danbainoso.shared.models.CallRequest;
import org.example.danbainoso.shared.models.Friendship;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ChatController implements ChatClientCallback, VideoClientCallback {
    private static final Logger logger = LoggerUtil.getLogger(ChatController.class);
    private static final DateTimeFormatter TODAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final String DEFAULT_MESSAGE_PROMPT = "Nhập tin nhắn...";
    
    @FXML
    private ListView<String> contactsList;
    
    @FXML
    private ListView<String> groupsList;
    
    @FXML
    private TextField messageField;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private Button createGroupButton;
    
    @FXML
    private Button manageFriendsButton;
    
    @FXML
    private Button addFriendButton;

    @FXML
    private Button blockButton;

    @FXML
    private Button groupSettingsButton;

    @FXML
    private Button leaveGroupButton;
    
    @FXML
    private Label currentChatLabel;
    
    @FXML
    private VBox messagesContainer;
    
    @FXML
    private ScrollPane messagesScrollPane;
    
    private ClientRMI clientRMI;
    private User currentUser;
    private User selectedContact;
    private Group selectedGroup;
    private MediaHandler mediaHandler;
    private ConversationLoader conversationLoader;
    private BlockStatus currentBlockStatus = BlockStatus.NONE;
    private boolean currentUserIsGroupAdmin = false;
    private final Label blockedInfoLabel = new Label();
    private final List<Group> cachedGroups = new ArrayList<>();
    
    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
        mediaHandler = new MediaHandler();
        conversationLoader = new ConversationLoader(
                clientRMI,
                messagesContainer,
                messagesScrollPane,
                () -> currentUser,
                () -> selectedContact,
                () -> selectedGroup,
                this::formatTimestamp,
                createMessageActionHandler(),
                this::showAlert,
                20
        );
        
        // Register callbacks
        clientRMI.registerCallbacks(this, this);
        
        // Setup event handlers
        contactsList.setOnMouseClicked(e -> {
            String selected = contactsList.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals("Tạo nhóm mới")) {
                loadContactChat(selected);
            }
        });
        
        groupsList.setOnMouseClicked(e -> {
            int index = groupsList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                loadGroupChat(index);
            }
        });
        
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        
        createGroupButton.setOnAction(e -> openCreateGroupWindow());
        manageFriendsButton.setOnAction(e -> openFriendshipWindow());
        addFriendButton.setOnAction(e -> sendFriendRequest());
        setAddFriendVisibility(false);
        blockButton.setVisible(false);
        blockButton.setManaged(false);
        blockButton.setOnAction(e -> toggleBlockStatus());
        groupSettingsButton.setVisible(false);
        groupSettingsButton.setManaged(false);
        groupSettingsButton.setOnAction(e -> openGroupSettingsWindow());
        leaveGroupButton.setVisible(false);
        leaveGroupButton.setManaged(false);
        leaveGroupButton.setOnAction(e -> handleLeaveGroupShortcut());
        blockedInfoLabel.getStyleClass().add("system-message");
        blockedInfoLabel.setWrapText(true);
        
        updateGroupActionButtons();
        // Data will be loaded after setCurrentUser() is called
    }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        clientRMI.setCurrentUser(user);
        try {
            clientRMI.updateUserStatus(User.UserStatus.ONLINE);
        } catch (Exception e) {
            logger.error("Failed to update user status", e);
        }
        // Load data after user is set
        loadContacts();
        loadGroups();
    }
    
    private void loadContacts() {
        if (currentUser == null) {
            return;
        }
        try {
            List<User> users = clientRMI.searchUsers("");
            contactsList.getItems().clear();
            contactsList.getItems().add("Tạo nhóm mới");
            for (User user : users) {
                if (user.getUserId() != currentUser.getUserId()) {
                    contactsList.getItems().add(user.getUsername() + " (" + user.getStatus() + ")");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load contacts", e);
        }
    }
    
    private void loadGroups() {
        if (currentUser == null) {
            return;
        }
        try {
            List<Group> groups = clientRMI.getUserGroups(currentUser.getUserId());
            cachedGroups.clear();
            cachedGroups.addAll(groups);
            groupsList.getItems().clear();
            for (Group group : groups) {
                groupsList.getItems().add(formatGroupListItem(group));
            }
        } catch (Exception e) {
            logger.error("Failed to load groups", e);
        }
    }
    
    private void loadContactChat(String contactName) {
        try {
            String username = contactName.split(" ")[0];
            List<User> users = clientRMI.searchUsers(username);
            for (User user : users) {
                if (user.getUsername().equals(username)) {
                    selectedContact = user;
                    selectedGroup = null;
                    currentUserIsGroupAdmin = false;
                    refreshGroupSettingsButtonState();
                    updateGroupActionButtons();
                    currentChatLabel.setText("Chat với: " + user.getUsername());
                    checkFriendshipStatus(user);
                    prepareContactConversation(user);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load contact chat", e);
        }
    }
    
    private void checkFriendshipStatus(User contact) {
        if (currentUser == null || contact == null || contact.getUserId() == currentUser.getUserId()) {
            setAddFriendVisibility(false);
            return;
        }
        
        new Thread(() -> {
            try {
                List<User> friends = clientRMI.getFriends();
                boolean isFriend = friends.stream()
                    .anyMatch(f -> f.getUserId() == contact.getUserId());
                
                Platform.runLater(() -> {
                    setAddFriendVisibility(!isFriend);
                });
            } catch (Exception e) {
                logger.error("Failed to check friendship status", e);
                Platform.runLater(() -> {
                    setAddFriendVisibility(true);
                });
            }
        }).start();
    }
    
    private void sendFriendRequest() {
        if (selectedContact == null || currentUser == null) {
            return;
        }
        
        new Thread(() -> {
            try {
                boolean success = clientRMI.sendFriendRequest(selectedContact.getUserId());
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Đã gửi lời mời kết bạn đến " + selectedContact.getUsername());
                        addFriendButton.setVisible(false);
                    } else {
                        showAlert("Không thể gửi lời mời kết bạn. Có thể đã gửi trước đó.");
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to send friend request", e);
                Platform.runLater(() -> {
                    showAlert("Lỗi khi gửi lời mời kết bạn.");
                });
            }
        }).start();
    }
    
    private void openFriendshipWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/danbainoso/client/ui/friendship.fxml"));
            Parent root = loader.load();
            
            FriendshipController friendshipController = loader.getController();
            friendshipController.setCurrentUser(currentUser);
            
            Stage stage = new Stage();
            stage.setTitle("Quản lý bạn bè");
            stage.setScene(new Scene(root, 500, 400));
            friendshipController.setStage(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open friendship window", e);
            showAlert("Lỗi khi mở cửa sổ quản lý bạn bè: " + e.getMessage());
        }
    }
    
    private void loadGroupChat(int index) {
        if (index < 0 || index >= cachedGroups.size()) {
            return;
        }
        try {
            Group group = cachedGroups.get(index);
            if (group == null) {
                return;
            }
            selectedGroup = group;
            selectedContact = null;
            currentUserIsGroupAdmin = false;
            refreshGroupSettingsButtonState();
            updateGroupActionButtons();
            setAddFriendVisibility(false);
            currentBlockStatus = BlockStatus.NONE;
            updateBlockButtonVisibility(false);
            enableMessaging();
            currentChatLabel.setText("Nhóm: " + group.getGroupName());
            conversationLoader.loadConversation(null, selectedGroup);
            fetchGroupRoleForSelectedGroup();
        } catch (Exception e) {
            logger.error("Failed to load group chat", e);
        }
    }
    
    
    @FXML
    private void sendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty()) {
            return;
        }
        
        if (selectedContact == null && selectedGroup == null) {
            return;
        }
        
        if (selectedContact != null && currentBlockStatus != BlockStatus.NONE) {
            if (currentBlockStatus == BlockStatus.BLOCKED_BY_ME) {
                showAlert("Bạn đã chặn người dùng này. Bỏ chặn để tiếp tục trò chuyện.");
            } else {
                showAlert("Bạn đã bị chặn bởi người dùng này.");
            }
            return;
        }

        try {
            Message message = new Message();
            message.setSenderId(currentUser.getUserId());
            message.setContent(content);
            message.setMessageType(Message.MessageType.TEXT);
            message.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            
            if (selectedContact != null) {
                message.setReceiverId(selectedContact.getUserId());
            } else if (selectedGroup != null) {
                message.setGroupId(selectedGroup.getGroupId());
            }
            
            clientRMI.sendMessage(message);
            messageField.clear();
        } catch (Exception e) {
            logger.error("Failed to send message", e);
        }
    }

    private void prepareContactConversation(User user) {
        if (user == null) {
            return;
        }
        selectedGroup = null;
        currentBlockStatus = BlockStatus.NONE;
        updateBlockButtonVisibility(true);
        blockButton.setDisable(true);
        messageField.setDisable(true);
        sendButton.setDisable(true);
        showPlaceholderMessage("Đang tải cuộc trò chuyện...");
        fetchBlockStatus(user);
    }

    private void fetchBlockStatus(User contact) {
        if (currentUser == null || contact == null) {
            return;
        }
        final int targetId = contact.getUserId();
        new Thread(() -> {
            BlockStatus status = BlockStatus.NONE;
            try {
                status = clientRMI.getBlockStatus(targetId);
            } catch (Exception e) {
                logger.error("Failed to check block status", e);
            }
            BlockStatus finalStatus = status;
            Platform.runLater(() -> {
                if (selectedContact == null || selectedContact.getUserId() != targetId) {
                    return;
                }
                currentBlockStatus = finalStatus;
                updateBlockButtonState();
                if (finalStatus == BlockStatus.NONE) {
                    enableMessaging();
                    conversationLoader.loadConversation(selectedContact, null);
                } else {
                    showBlockedState(finalStatus);
                }
            });
        }).start();
    }

    private void toggleBlockStatus() {
        if (selectedContact == null) {
            return;
        }
        if (currentBlockStatus == BlockStatus.BLOCKED_BY_OTHER) {
            return;
        }
        blockButton.setDisable(true);
        final int targetId = selectedContact.getUserId();
        new Thread(() -> {
            try {
                boolean success;
                if (currentBlockStatus == BlockStatus.BLOCKED_BY_ME) {
                    success = clientRMI.unblockUser(targetId);
                } else {
                    success = clientRMI.blockUser(targetId);
                }
                boolean finalSuccess = success;
                Platform.runLater(() -> {
                    blockButton.setDisable(false);
                    if (!finalSuccess) {
                        showAlert("Không thể cập nhật trạng thái chặn.");
                        return;
                    }
                    currentBlockStatus = (currentBlockStatus == BlockStatus.BLOCKED_BY_ME)
                            ? BlockStatus.NONE
                            : BlockStatus.BLOCKED_BY_ME;
                    updateBlockButtonState();
                    if (currentBlockStatus == BlockStatus.BLOCKED_BY_ME) {
                        showAlert("Đã chặn " + selectedContact.getUsername());
                        showBlockedState(currentBlockStatus);
                    } else {
                        showAlert("Đã bỏ chặn " + selectedContact.getUsername());
                        enableMessaging();
                        conversationLoader.loadConversation(selectedContact, null);
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to toggle block state", e);
                Platform.runLater(() -> {
                    blockButton.setDisable(false);
                    showAlert("Lỗi khi cập nhật trạng thái chặn.");
                });
            }
        }).start();
    }

    private void showBlockedState(BlockStatus status) {
        messageField.clear();
        messageField.setDisable(true);
        sendButton.setDisable(true);
        String prompt = status == BlockStatus.BLOCKED_BY_OTHER
                ? "Bạn đã bị chặn"
                : "Bạn đã chặn người dùng này";
        messageField.setPromptText(prompt);
        String username = selectedContact != null ? selectedContact.getUsername() : "người dùng này";
        if (status == BlockStatus.BLOCKED_BY_OTHER) {
            showPlaceholderMessage(username + " đã chặn bạn. Bạn không thể xem hoặc gửi tin nhắn.");
        } else {
            showPlaceholderMessage("Bạn đã chặn " + username + ". Bỏ chặn để xem lại tin nhắn.");
        }
    }

    private void enableMessaging() {
        messageField.setDisable(false);
        sendButton.setDisable(false);
        messageField.setPromptText(DEFAULT_MESSAGE_PROMPT);
        messagesContainer.getChildren().remove(blockedInfoLabel);
    }

    private void updateBlockButtonVisibility(boolean visible) {
        blockButton.setVisible(visible);
        blockButton.setManaged(visible);
        if (visible) {
            updateBlockButtonState();
        }
    }

    private void updateBlockButtonState() {
        switch (currentBlockStatus) {
            case BLOCKED_BY_OTHER:
                blockButton.setText("Đã bị chặn");
                blockButton.setDisable(true);
                break;
            case BLOCKED_BY_ME:
                blockButton.setText("Bỏ chặn");
                blockButton.setDisable(false);
                break;
            case NONE:
            default:
                blockButton.setText("Chặn");
                blockButton.setDisable(false);
                break;
        }
    }

    private void showPlaceholderMessage(String message) {
        messagesContainer.getChildren().clear();
        blockedInfoLabel.setText(message);
        messagesContainer.getChildren().add(blockedInfoLabel);
    }

    private void fetchGroupRoleForSelectedGroup() {
        if (selectedGroup == null || currentUser == null) {
            currentUserIsGroupAdmin = false;
            refreshGroupSettingsButtonState();
            return;
        }
        final int currentGroupId = selectedGroup.getGroupId();
        currentUserIsGroupAdmin = false;
        refreshGroupSettingsButtonState();
        new Thread(() -> {
            Group.GroupRole role = null;
            try {
                role = clientRMI.getGroupRole(currentGroupId);
            } catch (Exception e) {
                logger.error("Failed to fetch group role", e);
            }
            Group.GroupRole finalRole = role;
            Platform.runLater(() -> {
                if (selectedGroup == null || selectedGroup.getGroupId() != currentGroupId) {
                    return;
                }
                currentUserIsGroupAdmin = finalRole == Group.GroupRole.ADMIN;
                refreshGroupSettingsButtonState();
                updateGroupActionButtons();
            });
        }).start();
    }

    private void refreshGroupSettingsButtonState() {
        boolean visible = currentUserIsGroupAdmin && selectedGroup != null;
        groupSettingsButton.setVisible(visible);
        groupSettingsButton.setManaged(visible);
        groupSettingsButton.setDisable(!visible);
    }

    private void setAddFriendVisibility(boolean visible) {
        addFriendButton.setVisible(visible);
        addFriendButton.setManaged(visible);
    }

    private void updateGroupActionButtons() {
        boolean hasGroup = selectedGroup != null;
        boolean isAdmin = currentUserIsGroupAdmin && hasGroup;

        groupSettingsButton.setVisible(isAdmin);
        groupSettingsButton.setManaged(isAdmin);
        groupSettingsButton.setDisable(!isAdmin);

        boolean canLeave = hasGroup && !currentUserIsGroupAdmin;
        leaveGroupButton.setVisible(canLeave);
        leaveGroupButton.setManaged(canLeave);
        leaveGroupButton.setDisable(!canLeave);
    }

    private String formatGroupListItem(Group group) {
        return group.getGroupName() + " (" + group.getMemberCount() + " thành viên)";
    }

    private void openGroupSettingsWindow() {
        if (selectedGroup == null || !currentUserIsGroupAdmin) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/danbainoso/client/ui/group_settings.fxml"));
            Parent root = loader.load();

            GroupSettingsController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("Cài đặt nhóm");
            stage.setScene(new Scene(root, 560, 520));
            controller.setStage(stage);
            Group contextGroup = selectedGroup;
            controller.setData(
                    currentUser,
                    contextGroup,
                    currentUserIsGroupAdmin,
                    this::handleGroupDetailsUpdated,
                    () -> handleGroupDeleted(contextGroup.getGroupId())
            );
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open group settings window", e);
            showAlert("Không thể mở cài đặt nhóm.");
        }
    }

    private void handleGroupDetailsUpdated(Group updatedGroup) {
        if (updatedGroup == null || selectedGroup == null || updatedGroup.getGroupId() != selectedGroup.getGroupId()) {
            return;
        }
        selectedGroup = updatedGroup;
        currentChatLabel.setText("Nhóm: " + updatedGroup.getGroupName());
        loadGroups();
    }

    private void handleGroupDeleted(int groupId) {
        if (selectedGroup != null && selectedGroup.getGroupId() == groupId) {
            selectedGroup = null;
            messagesContainer.getChildren().clear();
            showPlaceholderMessage("Nhóm đã bị xóa. Chọn cuộc trò chuyện khác.");
            currentChatLabel.setText("Chọn một cuộc trò chuyện");
            messageField.clear();
            messageField.setDisable(true);
            sendButton.setDisable(true);
        }
        currentUserIsGroupAdmin = false;
        refreshGroupSettingsButtonState();
        updateGroupActionButtons();
        setAddFriendVisibility(false);
        updateBlockButtonVisibility(false);
        loadGroups();
    }

    private void handleLeaveGroupShortcut() {
        if (selectedGroup == null || currentUser == null) {
            return;
        }
        if (currentUserIsGroupAdmin) {
            showAlert("Admin phải dùng Cài đặt nhóm để chuyển quyền hoặc xóa nhóm trước khi rời.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Rời nhóm");
        confirm.setHeaderText("Bạn chắc chắn muốn rời nhóm " + selectedGroup.getGroupName() + "?");
        confirm.setContentText("Bạn sẽ không nhận được tin nhắn từ nhóm này nữa.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        leaveGroupButton.setDisable(true);
        int groupId = selectedGroup.getGroupId();
        new Thread(() -> {
            try {
                boolean left = clientRMI.removeMemberFromGroup(groupId, currentUser.getUserId());
                Platform.runLater(() -> {
                    leaveGroupButton.setDisable(false);
                    if (left) {
                        showAlert("Bạn đã rời nhóm.");
                        handleGroupDeleted(groupId);
                    } else {
                        showAlert("Không thể rời nhóm lúc này. Hãy thử lại.");
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to leave group", e);
                Platform.runLater(() -> {
                    leaveGroupButton.setDisable(false);
                    showAlert("Lỗi khi rời nhóm: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void openCreateGroupWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/danbainoso/client/ui/group.fxml"));
            Parent root = loader.load();
            
            GroupController groupController = loader.getController();
            groupController.setCurrentUser(currentUser);
            
            Stage stage = new Stage();
            stage.setTitle("Tạo nhóm mới");
            stage.setScene(new Scene(root, 500, 400));
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open create group window", e);
        }
    }
    
    // ChatClientCallback implementation
    @Override
    public void onMessageReceived(Message message) throws RemoteException {
        mediaHandler.playNotificationSound();
        conversationLoader.handleIncomingMessage(message);
    }
    
    @Override
    public void onUserStatusChanged(int userId, User.UserStatus status) throws RemoteException {
        Platform.runLater(() -> {
            loadContacts();
        });
    }
    
    @Override
    public void onUserJoinedGroup(int groupId, User user) throws RemoteException {
        Platform.runLater(() -> {
            if (selectedGroup != null && selectedGroup.getGroupId() == groupId) {
                displaySystemMessage(String.format("%s đã tham gia nhóm", user.getUsername()));
            }
            loadGroups();
        });
    }
    
    @Override
    public void onUserLeftGroup(int groupId, int userId) throws RemoteException {
        Platform.runLater(() -> {
            if (selectedGroup != null && selectedGroup.getGroupId() == groupId) {
                displaySystemMessage(String.format("Người dùng %d đã rời nhóm", userId));
            }
            loadGroups();
        });
    }
    
    @Override
    public void onMessagesMarkedAsRead(int readerId, int senderId) throws RemoteException {
        conversationLoader.handleMessagesMarkedAsRead(readerId, senderId);
    }
    
    @Override
    public void onMessageUpdated(Message message) throws RemoteException {
        conversationLoader.handleMessageUpdated(message);
    }
    
    @Override
    public void onMessageDeleted(int messageId) throws RemoteException {
        conversationLoader.handleMessageDeleted(messageId);
    }
    
    @Override
    public void onFriendRequestReceived(Friendship friendship) throws RemoteException {
        new Thread(() -> {
            String senderName = "Người dùng " + friendship.getUser1Id();
            try {
                User sender = clientRMI.getUserById(friendship.getUser1Id());
                if (sender != null && sender.getUsername() != null) {
                    senderName = sender.getUsername();
                }
            } catch (RemoteException e) {
                logger.error("Failed to fetch sender info", e);
            }
            
            String finalSenderName = senderName;
            Platform.runLater(() -> {
                showAlert("Bạn có lời mời kết bạn từ " + finalSenderName);
                // Reload contacts to show updated status
                loadContacts();
            });
        }).start();
    }
    
    // VideoClientCallback implementation
    @Override
    public void onIncomingCall(CallRequest callRequest) throws RemoteException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Cuộc gọi đến");
            alert.setHeaderText("Bạn có cuộc gọi đến từ: " + callRequest.getCallerName());
            alert.setContentText("Bạn có muốn chấp nhận không?");
            
            ButtonType acceptButton = new ButtonType("Chấp nhận");
            ButtonType rejectButton = new ButtonType("Từ chối");
            alert.getButtonTypes().setAll(acceptButton, rejectButton);
            
            alert.showAndWait().ifPresent(type -> {
                try {
                    if (type == acceptButton) {
                        clientRMI.acceptCall(callRequest.getCallId());
                    } else {
                        clientRMI.rejectCall(callRequest.getCallId());
                    }
                } catch (Exception e) {
                    logger.error("Failed to handle call", e);
                }
            });
        });
    }
    
    @Override
    public void onCallAccepted(String callId) throws RemoteException {
        Platform.runLater(() -> {
            displaySystemMessage("Cuộc gọi đã được chấp nhận");
        });
    }
    
    @Override
    public void onCallRejected(String callId) throws RemoteException {
        Platform.runLater(() -> {
            displaySystemMessage("Cuộc gọi đã bị từ chối");
        });
    }
    
    @Override
    public void onCallEnded(String callId) throws RemoteException {
        Platform.runLater(() -> {
            displaySystemMessage("Cuộc gọi đã kết thúc");
        });
    }
    
    private void displaySystemMessage(String message) {
        conversationLoader.displaySystemMessage(message);
    }
    
    private MessageViewFactory.MessageActionHandler createMessageActionHandler() {
        return new MessageViewFactory.MessageActionHandler() {
            @Override
            public boolean editMessage(int messageId, String newContent) throws RemoteException {
                return clientRMI.editMessage(messageId, newContent);
            }

            @Override
            public boolean deleteMessage(int messageId) throws RemoteException {
                return clientRMI.deleteMessage(messageId);
            }

            @Override
            public void showAlert(String message) {
                ChatController.this.showAlert(message);
            }
        };
    }
    
    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        LocalDateTime messageTime = LocalDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
        LocalDate messageDate = messageTime.toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        
        if (messageDate.isEqual(today)) {
            return messageTime.format(TODAY_FORMATTER);
        }
        return messageTime.format(DEFAULT_FORMATTER);
    }
    
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
