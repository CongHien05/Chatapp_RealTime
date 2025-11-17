package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.danbainoso.client.ClientMain;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.shared.models.Friendship;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.RemoteException;
import java.util.List;

public class FriendshipController {
    private static final Logger logger = LoggerUtil.getLogger(FriendshipController.class);
    
    @FXML
    private ListView<String> friendsList;
    
    @FXML
    private ListView<String> friendRequestsList;
    
    @FXML
    private TabPane friendshipTabs;
    
    @FXML
    private Button closeButton;
    
    private ClientRMI clientRMI;
    private User currentUser;
    private Stage stage;
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadFriends();
        loadFriendRequests();
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
        
        // Setup context menu for friend requests
        friendRequestsList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText(item);
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem acceptItem = new MenuItem("Chấp nhận");
                    MenuItem rejectItem = new MenuItem("Từ chối");
                    
                    acceptItem.setOnAction(e -> acceptFriendRequest(item));
                    rejectItem.setOnAction(e -> rejectFriendRequest(item));
                    
                    contextMenu.getItems().addAll(acceptItem, rejectItem);
                    setContextMenu(contextMenu);
                }
            }
        });
        
        // Setup context menu for friends
        friendsList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText(item);
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem removeItem = new MenuItem("Xóa bạn");
                    
                    removeItem.setOnAction(e -> removeFriend(item));
                    
                    contextMenu.getItems().add(removeItem);
                    setContextMenu(contextMenu);
                }
            }
        });
    }
    
    private void loadFriends() {
        new Thread(() -> {
            try {
                List<User> friends = clientRMI.getFriends();
                Platform.runLater(() -> {
                    friendsList.getItems().clear();
                    if (friends.isEmpty()) {
                        friendsList.getItems().add("Chưa có bạn bè");
                    } else {
                        for (User friend : friends) {
                            friendsList.getItems().add(friend.getUsername() + " (" + friend.getStatus() + ")");
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to load friends", e);
                Platform.runLater(() -> {
                    showAlert("Không thể tải danh sách bạn bè.");
                });
            }
        }).start();
    }
    
    private void loadFriendRequests() {
        new Thread(() -> {
            try {
                List<Friendship> requests = clientRMI.getFriendRequests();
                Platform.runLater(() -> {
                    friendRequestsList.getItems().clear();
                    if (requests.isEmpty()) {
                        friendRequestsList.getItems().add("Không có lời mời kết bạn");
                    } else {
                        for (Friendship request : requests) {
                            int senderId = request.getUser1Id();
                            try {
                                User sender = clientRMI.getUserById(senderId);
                                if (sender != null) {
                                    friendRequestsList.getItems().add(sender.getUsername() + " (ID: " + request.getFriendshipId() + ")");
                                } else {
                                    friendRequestsList.getItems().add("Người dùng " + senderId + " (ID: " + request.getFriendshipId() + ")");
                                }
                            } catch (RemoteException e) {
                                friendRequestsList.getItems().add("Người dùng " + senderId + " (ID: " + request.getFriendshipId() + ")");
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to load friend requests", e);
                Platform.runLater(() -> {
                    showAlert("Không thể tải lời mời kết bạn.");
                });
            }
        }).start();
    }
    
    private void acceptFriendRequest(String item) {
        try {
            int friendshipId = extractFriendshipId(item);
            if (friendshipId > 0) {
                boolean success = clientRMI.acceptFriendRequest(friendshipId);
                if (success) {
                    showAlert("Đã chấp nhận lời mời kết bạn.");
                    loadFriends();
                    loadFriendRequests();
                } else {
                    showAlert("Không thể chấp nhận lời mời kết bạn.");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to accept friend request", e);
            showAlert("Lỗi khi chấp nhận lời mời kết bạn.");
        }
    }
    
    private void rejectFriendRequest(String item) {
        try {
            int friendshipId = extractFriendshipId(item);
            if (friendshipId > 0) {
                boolean success = clientRMI.rejectFriendRequest(friendshipId);
                if (success) {
                    showAlert("Đã từ chối lời mời kết bạn.");
                    loadFriendRequests();
                } else {
                    showAlert("Không thể từ chối lời mời kết bạn.");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to reject friend request", e);
            showAlert("Lỗi khi từ chối lời mời kết bạn.");
        }
    }
    
    private void removeFriend(String item) {
        try {
            String username = item.split(" ")[0];
            List<User> friends = clientRMI.getFriends();
            for (User friend : friends) {
                if (friend.getUsername().equals(username)) {
                    boolean success = clientRMI.removeFriend(friend.getUserId());
                    if (success) {
                        showAlert("Đã xóa bạn: " + username);
                        loadFriends();
                    } else {
                        showAlert("Không thể xóa bạn.");
                    }
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to remove friend", e);
            showAlert("Lỗi khi xóa bạn.");
        }
    }
    
    private int extractFriendshipId(String item) {
        try {
            int startIdx = item.indexOf("ID: ") + 4;
            int endIdx = item.indexOf(")", startIdx);
            if (startIdx > 3 && endIdx > startIdx) {
                return Integer.parseInt(item.substring(startIdx, endIdx).trim());
            }
        } catch (Exception e) {
            logger.error("Failed to extract friendship ID from: {}", item, e);
        }
        return -1;
    }
    
    @FXML
    private void closeDialog() {
        if (stage != null) {
            stage.close();
        }
    }
    
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

