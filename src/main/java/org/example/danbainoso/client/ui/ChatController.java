package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
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
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private ToggleButton btnTabContacts;
    
    @FXML
    private ToggleButton btnTabGroups;
    
    @FXML
    private VBox contactsView;
    
    @FXML
    private VBox groupsView;
    
    @FXML
    private Label statusLabel;
    
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
    private Button logoutButton;
    
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
    private final List<User> cachedContacts = new ArrayList<>();
    
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
        
        // Setup custom cell factories for modern UI
        setupContactsListCellFactory();
        setupGroupsListCellFactory();
        
        // Setup tab switching with ToggleButtons
        setupTabSwitching();
        
        // Setup event handlers
        contactsList.setOnMouseClicked(e -> {
            int index = contactsList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                loadContactByIndex(index);
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
        logoutButton.setOnAction(e -> handleLogout());
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
        
        // Setup window close handler to set status to OFFLINE
        setupWindowCloseHandler();
        
        // Load data after user is set
        loadContacts();
        loadGroups();
    }
    
    /**
     * Setup handler to update status to OFFLINE when window is closed
     */
    private void setupWindowCloseHandler() {
        Platform.runLater(() -> {
            Stage stage = (Stage) currentChatLabel.getScene().getWindow();
            if (stage != null) {
                stage.setOnCloseRequest(event -> {
                    logger.info("Window closing, updating user status to OFFLINE");
                    try {
                        if (currentUser != null) {
                            clientRMI.updateUserStatus(User.UserStatus.OFFLINE);
                            // Give server time to notify other clients
                            Thread.sleep(200);
                            clientRMI.unregisterCallbacks();
                            logger.info("User {} status set to OFFLINE on window close", currentUser.getUsername());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Status update interrupted on window close", e);
                    } catch (Exception e) {
                        logger.error("Failed to update status on window close", e);
                    }
                });
            }
        });
    }
    
    private void loadContacts() {
        if (currentUser == null) {
            return;
        }
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
                    contactsList.getItems().clear();
                    contactsList.getItems().addAll(
                            cachedContacts.stream()
                                    .map(u -> u.getUsername() + " (" + u.getStatus() + ")")
                                    .collect(Collectors.toList())
                    );
                });
            } catch (Exception e) {
                logger.error("Failed to load contacts", e);
            }
        }).start();
    }
    
    private void loadGroups() {
        loadGroups(null);
    }

    private void loadGroups(Runnable onFinish) {
        if (currentUser == null) {
            if (onFinish != null) {
                onFinish.run();
            }
            return;
        }
        new Thread(() -> {
            try {
                List<Group> groups = clientRMI.getUserGroups(currentUser.getUserId());
                Platform.runLater(() -> {
                    cachedGroups.clear();
                    cachedGroups.addAll(groups);
                    groupsList.getItems().setAll(
                            groups.stream()
                                    .map(this::formatGroupListItem)
                                    .collect(Collectors.toList())
                    );
                    updateGroupActionButtons();
                    if (onFinish != null) {
                        onFinish.run();
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to load groups", e);
            }
        }).start();
    }
    
    private void loadContactByIndex(int contactIndex) {
        if (contactIndex < 0 || contactIndex >= cachedContacts.size()) {
            return;
        }
        User user = cachedContacts.get(contactIndex);
        loadContactChat(user);
    }

    private void loadContactChat(User user) {
        if (user == null) {
            return;
        }
        
        // Clear group selection khi chuyển sang contact
        groupsList.getSelectionModel().clearSelection();
        
        // Luôn load lại conversation để đảm bảo hiển thị đúng và scroll đúng vị trí
        selectedContact = user;
        selectedGroup = null;
        currentUserIsGroupAdmin = false;
        refreshGroupSettingsButtonState();
        updateGroupActionButtons();
        currentChatLabel.setText("Chat với: " + user.getUsername());
        
        // Update status label
        updateStatusLabel(user);
        
        checkFriendshipStatus(user);
        prepareContactConversation(user);
    }
    
    /**
     * Update the status label based on user's online status
     */
    private void updateStatusLabel(User user) {
        if (user != null && statusLabel != null) {
            if (user.getStatus() == User.UserStatus.ONLINE) {
                statusLabel.setText("● Đang hoạt động");
                statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");
            } else {
                statusLabel.setText("● Không hoạt động");
                statusLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");
            }
        } else if (statusLabel != null) {
            statusLabel.setText("");
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
            
            // Clear contact selection khi chuyển sang group
            contactsList.getSelectionModel().clearSelection();
            
            // Luôn load lại conversation để đảm bảo hiển thị đúng và scroll đúng vị trí
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
            
            // Update status label for group
            if (statusLabel != null) {
                try {
                    List<User> members = clientRMI.getGroupMembers(group.getGroupId());
                    statusLabel.setText(members.size() + " thành viên");
                    statusLabel.setStyle("-fx-text-fill: #95a5a6;");
                } catch (Exception e) {
                    statusLabel.setText("");
                }
            }
            
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
            groupController.setOnGroupCreated(createdGroup ->
                    loadGroups(() -> selectGroupById(createdGroup.getGroupId())));
            
            Stage stage = new Stage();
            stage.setTitle("Tạo nhóm mới");
            stage.setScene(new Scene(root, 500, 400));
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open create group window", e);
        }
    }

    private void selectGroupById(int groupId) {
        for (int i = 0; i < cachedGroups.size(); i++) {
            if (cachedGroups.get(i).getGroupId() == groupId) {
                groupsList.getSelectionModel().select(i);
                loadGroupChat(i);
                return;
            }
        }
    }
    
    // ChatClientCallback implementation
    @Override
    public void onMessageReceived(Message message) throws RemoteException {
        mediaHandler.playNotificationSound();
        // handleIncomingMessage đã tự check và thêm message vào UI nếu thuộc conversation hiện tại
        // Không cần reload lại vì sẽ gây duplicate hoặc sai thứ tự
        conversationLoader.handleIncomingMessage(message);
    }
    
    @Override
    public void onUserStatusChanged(int userId, User.UserStatus status) throws RemoteException {
        Platform.runLater(() -> {
            // Update the contacts list to reflect new status
            loadContacts();
            
            // If the user whose status changed is the currently selected contact, update status label
            if (selectedContact != null && selectedContact.getUserId() == userId) {
                selectedContact.setStatus(status);
                updateStatusLabel(selectedContact);
            }
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
        // handleMessageUpdated đã tự check và reload nếu cần
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
        try {
            // Chuyển timestamp về local time
            LocalDateTime messageTime = timestamp.toLocalDateTime();
            LocalDate messageDate = messageTime.toLocalDate();
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate tomorrow = today.plusDays(1);
            
            // Nếu timestamp từ DB sai (ngày mai), điều chỉnh về hôm nay
            if (messageDate.isEqual(tomorrow)) {
                logger.warn("Message timestamp is in the future, adjusting: {}", timestamp);
                messageTime = messageTime.minusDays(1);
                messageDate = messageTime.toLocalDate();
            }
            
            if (messageDate.isEqual(today)) {
                return messageTime.format(TODAY_FORMATTER);
            } else if (messageDate.isEqual(yesterday)) {
                return "Hôm qua " + messageTime.format(TODAY_FORMATTER);
            }
            return messageTime.format(DEFAULT_FORMATTER);
        } catch (Exception e) {
            logger.error("Failed to format timestamp: {}", timestamp, e);
            return timestamp.toString();
        }
    }

    private boolean isMessageForCurrentConversation(Message message) {
        if (message == null) {
            return false;
        }
        if (selectedGroup != null && message.getGroupId() != null) {
            return message.getGroupId() == selectedGroup.getGroupId();
        }
        if (selectedContact != null && message.getReceiverId() != null && currentUser != null) {
            int contactId = selectedContact.getUserId();
            int selfId = currentUser.getUserId();
            return (message.getSenderId() == contactId && message.getReceiverId() == selfId) ||
                    (message.getSenderId() == selfId && message.getReceiverId() == contactId);
        }
        return false;
    }
    
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Đăng xuất");
        confirm.setHeaderText("Bạn có chắc chắn muốn đăng xuất?");
        confirm.setContentText("Bạn sẽ cần đăng nhập lại để tiếp tục sử dụng.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Update status to offline FIRST before unregistering callbacks
                if (currentUser != null) {
                    logger.info("Updating user {} status to OFFLINE before logout", currentUser.getUsername());
                    clientRMI.updateUserStatus(User.UserStatus.OFFLINE);
                    
                    // Give server time to notify other clients about status change
                    Thread.sleep(200);
                }
                
                // Now unregister callbacks
                clientRMI.unregisterCallbacks();
                logger.info("Callbacks unregistered for user {}", currentUser != null ? currentUser.getUsername() : "unknown");
                
                // Get current stage and keep it
                Stage stage = (Stage) logoutButton.getScene().getWindow();
                
                // Load login screen
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/danbainoso/client/ui/login.fxml"));
                Parent root = loader.load();
                
                // Reuse the same stage (window) with proper size for login
                Scene loginScene = new Scene(root, 400, 350);
                loginScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
                stage.setScene(loginScene);
                stage.setTitle("Đăng nhập - Metus Chat");
                stage.centerOnScreen();
                
                logger.info("User logged out successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Logout interrupted", e);
                showAlert("Đăng xuất bị gián đoạn");
            } catch (Exception e) {
                logger.error("Failed to logout", e);
                showAlert("Lỗi khi đăng xuất: " + e.getMessage());
            }
        }
    }
    
    /**
     * Setup tab switching between Contacts and Groups using ToggleButtons
     */
    private void setupTabSwitching() {
        // Initially show contacts view
        contactsView.setVisible(true);
        contactsView.setManaged(true);
        groupsView.setVisible(false);
        groupsView.setManaged(false);
        
        // Listen to toggle button changes
        btnTabContacts.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                contactsView.setVisible(true);
                contactsView.setManaged(true);
                groupsView.setVisible(false);
                groupsView.setManaged(false);
            }
        });
        
        btnTabGroups.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                contactsView.setVisible(false);
                contactsView.setManaged(false);
                groupsView.setVisible(true);
                groupsView.setManaged(true);
            }
        });
    }
    
    /**
     * Setup custom cell factory for contacts list with modern UI
     */
    private void setupContactsListCellFactory() {
        contactsList.setCellFactory(lv -> new ListCell<String>() {
            private final HBox container = new HBox(12);
            private final Label avatarLabel = new Label();
            private final VBox textContainer = new VBox(2);
            private final HBox nameRow = new HBox(6);
            private final Label nameLabel = new Label();
            private final Label statusDot = new Label("●");
            private final Label statusLabel = new Label();
            
            {
                // Avatar styling
                avatarLabel.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-min-width: 44px;" +
                    "-fx-min-height: 44px;" +
                    "-fx-max-width: 44px;" +
                    "-fx-max-height: 44px;" +
                    "-fx-alignment: center;" +
                    "-fx-background-radius: 22px;"
                );
                
                // Name styling
                nameLabel.setStyle(
                    "-fx-font-size: 14px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: #212121;"
                );
                
                // Status dot styling
                statusDot.setStyle(
                    "-fx-font-size: 10px;" +
                    "-fx-padding: 0;"
                );
                
                // Status text styling
                statusLabel.setStyle(
                    "-fx-font-size: 12px;" +
                    "-fx-text-fill: #65676b;"
                );
                
                nameRow.getChildren().addAll(nameLabel);
                nameRow.setAlignment(Pos.CENTER_LEFT);
                
                HBox statusRow = new HBox(4);
                statusRow.getChildren().addAll(statusDot, statusLabel);
                statusRow.setAlignment(Pos.CENTER_LEFT);
                
                textContainer.getChildren().addAll(nameRow, statusRow);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                container.getChildren().addAll(avatarLabel, textContainer);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(8, 12, 8, 12));
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Get user from cached list
                    int index = getIndex();
                    if (index >= 0 && index < cachedContacts.size()) {
                        User user = cachedContacts.get(index);
                        
                        // Set avatar (first letter of username)
                        String initial = user.getUsername().substring(0, 1).toUpperCase();
                        avatarLabel.setText(initial);
                        
                        // Set name
                        nameLabel.setText(user.getUsername());
                        
                        // Set status with colored dot
                        if (user.getStatus() == User.UserStatus.ONLINE) {
                            statusDot.setStyle("-fx-font-size: 10px; -fx-text-fill: #2ecc71; -fx-padding: 0;");
                            statusLabel.setText("Đang hoạt động");
                            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
                        } else {
                            statusDot.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6; -fx-padding: 0;");
                            statusLabel.setText("Không hoạt động");
                            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #95a5a6;");
                        }
                    } else {
                        // Fallback for simple string display
                        avatarLabel.setText(item.substring(0, 1).toUpperCase());
                        nameLabel.setText(item);
                        statusDot.setVisible(false);
                        statusLabel.setText("");
                    }
                    
                    setGraphic(container);
                    setText(null);
                }
            }
        });
    }
    
    /**
     * Setup custom cell factory for groups list with modern UI
     */
    private void setupGroupsListCellFactory() {
        groupsList.setCellFactory(lv -> new ListCell<String>() {
            private final HBox container = new HBox(12);
            private final Label avatarLabel = new Label();
            private final VBox textContainer = new VBox(2);
            private final Label nameLabel = new Label();
            private final Label membersLabel = new Label();
            
            {
                // Avatar styling (different gradient for groups)
                avatarLabel.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #f093fb, #f5576c);" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-min-width: 44px;" +
                    "-fx-min-height: 44px;" +
                    "-fx-max-width: 44px;" +
                    "-fx-max-height: 44px;" +
                    "-fx-alignment: center;" +
                    "-fx-background-radius: 22px;"
                );
                
                // Name styling
                nameLabel.setStyle(
                    "-fx-font-size: 14px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: #212121;"
                );
                
                // Members count styling
                membersLabel.setStyle(
                    "-fx-font-size: 12px;" +
                    "-fx-text-fill: #65676b;"
                );
                
                textContainer.getChildren().addAll(nameLabel, membersLabel);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                container.getChildren().addAll(avatarLabel, textContainer);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(8, 12, 8, 12));
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Get group from cached list
                    int index = getIndex();
                    if (index >= 0 && index < cachedGroups.size()) {
                        Group group = cachedGroups.get(index);
                        
                        // Set avatar (group icon)
                        avatarLabel.setText("👥");
                        
                        // Set name
                        nameLabel.setText(group.getGroupName());
                        
                        // Set members count (we'll fetch this)
                        try {
                            List<User> members = clientRMI.getGroupMembers(group.getGroupId());
                            membersLabel.setText(members.size() + " thành viên");
                        } catch (Exception e) {
                            membersLabel.setText("Nhóm");
                        }
                    } else {
                        // Fallback for simple string display
                        avatarLabel.setText("👥");
                        nameLabel.setText(item);
                        membersLabel.setText("");
                    }
                    
                    setGraphic(container);
                    setText(null);
                }
            }
        });
    }
}
