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
import org.example.danbainoso.shared.models.*;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.List;

public class ChatController implements ChatClientCallback, VideoClientCallback {
    private static final Logger logger = LoggerUtil.getLogger(ChatController.class);
    
    @FXML
    private ListView<String> contactsList;
    
    @FXML
    private ListView<String> groupsList;
    
    @FXML
    private TextArea messageArea;
    
    @FXML
    private TextField messageField;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private Button createGroupButton;
    
    @FXML
    private Label currentChatLabel;
    
    @FXML
    private VBox messagesContainer;
    
    private ClientRMI clientRMI;
    private User currentUser;
    private User selectedContact;
    private Group selectedGroup;
    private MediaHandler mediaHandler;
    
    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
        mediaHandler = new MediaHandler();
        
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
            String selected = groupsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadGroupChat(selected);
            }
        });
        
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        
        createGroupButton.setOnAction(e -> openCreateGroupWindow());
        
        // Load initial data
        loadContacts();
        loadGroups();
    }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        clientRMI.setCurrentUser(user);
        try {
            clientRMI.updateUserStatus(User.UserStatus.ONLINE);
        } catch (Exception e) {
            logger.error("Failed to update user status", e);
        }
    }
    
    private void loadContacts() {
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
        try {
            List<Group> groups = clientRMI.getUserGroups(currentUser.getUserId());
            groupsList.getItems().clear();
            for (Group group : groups) {
                groupsList.getItems().add(group.getGroupName() + " (" + group.getMemberCount() + " thành viên)");
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
                    currentChatLabel.setText("Chat với: " + user.getUsername());
                    loadMessages();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load contact chat", e);
        }
    }
    
    private void loadGroupChat(String groupName) {
        try {
            String name = groupName.split(" ")[0];
            List<Group> groups = clientRMI.getUserGroups(currentUser.getUserId());
            for (Group group : groups) {
                if (group.getGroupName().equals(name)) {
                    selectedGroup = group;
                    selectedContact = null;
                    currentChatLabel.setText("Nhóm: " + group.getGroupName());
                    loadMessages();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load group chat", e);
        }
    }
    
    private void loadMessages() {
        messageArea.clear();
        try {
            List<Message> messages;
            if (selectedContact != null) {
                messages = clientRMI.getPrivateMessages(currentUser.getUserId(), selectedContact.getUserId(), 50);
            } else if (selectedGroup != null) {
                messages = clientRMI.getGroupMessages(selectedGroup.getGroupId(), 50);
            } else {
                return;
            }
            
            for (Message msg : messages) {
                displayMessage(msg);
            }
        } catch (Exception e) {
            logger.error("Failed to load messages", e);
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
    
    private void displayMessage(Message message) {
        Platform.runLater(() -> {
            String senderName = message.getSenderId() == currentUser.getUserId() ? "Bạn" : 
                               (message.getSenderName() != null ? message.getSenderName() : "Người dùng");
            String timestamp = message.getCreatedAt() != null ? 
                              message.getCreatedAt().toString().substring(11, 16) : "";
            
            String messageText = String.format("[%s] %s: %s\n", timestamp, senderName, message.getContent());
            messageArea.appendText(messageText);
        });
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
        Platform.runLater(() -> {
            mediaHandler.playNotificationSound();
            if ((selectedContact != null && message.getSenderId() == selectedContact.getUserId()) ||
                (selectedGroup != null && message.getGroupId() == selectedGroup.getGroupId())) {
                displayMessage(message);
            }
        });
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
                messageArea.appendText(String.format("[System] %s đã tham gia nhóm\n", user.getUsername()));
            }
            loadGroups();
        });
    }
    
    @Override
    public void onUserLeftGroup(int groupId, int userId) throws RemoteException {
        Platform.runLater(() -> {
            if (selectedGroup != null && selectedGroup.getGroupId() == groupId) {
                messageArea.appendText(String.format("[System] Người dùng %d đã rời nhóm\n", userId));
            }
            loadGroups();
        });
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
            messageArea.appendText("[System] Cuộc gọi đã được chấp nhận\n");
        });
    }
    
    @Override
    public void onCallRejected(String callId) throws RemoteException {
        Platform.runLater(() -> {
            messageArea.appendText("[System] Cuộc gọi đã bị từ chối\n");
        });
    }
    
    @Override
    public void onCallEnded(String callId) throws RemoteException {
        Platform.runLater(() -> {
            messageArea.appendText("[System] Cuộc gọi đã kết thúc\n");
        });
    }
}
