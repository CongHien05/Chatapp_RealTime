package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages conversation pagination, rendering, and updates.
 */
public class ConversationLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConversationLoader.class);

    private final ClientRMI clientRMI;
    private final VBox messagesContainer;
    private final ScrollPane messagesScrollPane;
    private final Supplier<User> currentUserSupplier;
    private final Supplier<User> selectedContactSupplier;
    private final Supplier<Group> selectedGroupSupplier;
    private final Function<Timestamp, String> timestampFormatter;
    private final MessageViewFactory.MessageActionHandler messageActionHandler;
    private final Consumer<String> alertConsumer;
    private final int pageSize;

    private final Button loadMoreButton;
    private final Label emptyStateLabel;
    private final List<Message> currentMessages = new ArrayList<>();

    private int currentOffset = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private int conversationVersion = 0;

    public ConversationLoader(ClientRMI clientRMI,
                              VBox messagesContainer,
                              ScrollPane messagesScrollPane,
                              Supplier<User> currentUserSupplier,
                              Supplier<User> selectedContactSupplier,
                              Supplier<Group> selectedGroupSupplier,
                              Function<Timestamp, String> timestampFormatter,
                              MessageViewFactory.MessageActionHandler messageActionHandler,
                              Consumer<String> alertConsumer,
                              int pageSize) {
        this.clientRMI = Objects.requireNonNull(clientRMI);
        this.messagesContainer = Objects.requireNonNull(messagesContainer);
        this.messagesScrollPane = Objects.requireNonNull(messagesScrollPane);
        this.currentUserSupplier = Objects.requireNonNull(currentUserSupplier);
        this.selectedContactSupplier = Objects.requireNonNull(selectedContactSupplier);
        this.selectedGroupSupplier = Objects.requireNonNull(selectedGroupSupplier);
        this.timestampFormatter = Objects.requireNonNull(timestampFormatter);
        this.messageActionHandler = Objects.requireNonNull(messageActionHandler);
        this.alertConsumer = Objects.requireNonNull(alertConsumer);
        this.pageSize = pageSize;

        this.loadMoreButton = new Button("Tải thêm");
        this.loadMoreButton.getStyleClass().add("load-more-button");
        this.loadMoreButton.setOnAction(e -> loadMoreMessages(conversationVersion));

        this.emptyStateLabel = new Label("Chưa có tin nhắn");
        this.emptyStateLabel.getStyleClass().add("empty-message");
    }

    public void loadConversation(User contact, Group group) {
        conversationVersion++;
        final int versionSnapshot = conversationVersion;
        
        logger.debug("Loading conversation for contact={}, group={}", 
                contact != null ? contact.getUsername() : "null", 
                group != null ? group.getGroupName() : "null");

        // Reset state NGAY LẬP TỨC trước khi load
        currentMessages.clear();
        currentOffset = 0;
        isLoading = false;
        hasMore = true;
        
        Platform.runLater(() -> {
            messagesContainer.getChildren().clear();
            updateLoadMoreButtonVisibility();
            showEmptyState(true);
        });
        
        // Load messages ngay lập tức với offset=0 để lấy tin mới nhất
        loadMoreMessages(versionSnapshot);
    }

    public void loadMoreMessages(int versionSnapshot) {
        if (isLoading || !isCurrentVersion(versionSnapshot)) {
            return;
        }
        User currentUser = currentUserSupplier.get();
        if (currentUser == null) {
            return;
        }
        if (selectedContactSupplier.get() == null && selectedGroupSupplier.get() == null) {
            return;
        }

        isLoading = true;
        Platform.runLater(() -> {
            loadMoreButton.setText("Đang tải...");
            loadMoreButton.setDisable(true);
            updateLoadMoreButtonVisibility();
        });

        final int requestOffset = currentOffset;
        final boolean initialFetch = requestOffset == 0;
        
        new Thread(() -> {
            try {
                logger.debug("Fetching messages: offset={}, limit={}", requestOffset, pageSize);
                List<Message> fetched = fetchMessages(requestOffset, pageSize);
                logger.debug("Fetched {} messages", fetched.size());
                
                Platform.runLater(() -> {
                    if (!isCurrentVersion(versionSnapshot)) {
                        logger.debug("Version mismatch, ignoring fetch result");
                        return;
                    }
                    
                    if (fetched.size() < pageSize) {
                        hasMore = false;
                    }
                    currentOffset += fetched.size();
                    
                    if (initialFetch) {
                        // Load lần đầu: add vào cuối để tin mới nhất ở cuối
                        // Messages từ DB đã được reverse trong MessageDAO để có chronological order (cũ → mới)
                        // Vậy khi add vào cuối, tin mới nhất sẽ ở cuối danh sách
                        currentMessages.addAll(fetched);
                        logger.debug("Initial fetch: {} messages loaded", currentMessages.size());
                        
                        if (currentMessages.isEmpty()) {
                            showEmptyState(true);
                        } else {
                            showEmptyState(false);
                            rebuildMessageList();
                            // Scroll xuống cuối sau khi render xong - áp dụng cho cả private và group
                            scrollToBottomDelayed();
                        }
                        // Chỉ mark as read cho private chat, không áp dụng cho group
                        if (selectedContactSupplier.get() != null) {
                            markPrivateChatAsRead();
                        }
                    } else {
                        // Load thêm tin cũ: add vào đầu
                        // Messages từ DB đã được reverse, nên tin cũ nhất trong batch sẽ ở đầu list
                        currentMessages.addAll(0, fetched);
                        addMessagesAtTop(fetched);
                        logger.debug("Load more: {} messages added, total={}", fetched.size(), currentMessages.size());
                        // Không scroll khi load tin cũ, giữ nguyên vị trí scroll hiện tại
                    }

                    isLoading = false;
                    loadMoreButton.setText("Tải thêm");
                    loadMoreButton.setDisable(false);
                    updateLoadMoreButtonVisibility();
                });
            } catch (Exception e) {
                logger.error("Failed to load more messages", e);
                Platform.runLater(() -> {
                    if (!isCurrentVersion(versionSnapshot)) {
                        return;
                    }
                    isLoading = false;
                    loadMoreButton.setText("Tải thêm");
                    loadMoreButton.setDisable(false);
                    updateLoadMoreButtonVisibility();
                    alertConsumer.accept("Không thể tải thêm tin nhắn.");
                });
            }
        }).start();
    }

    public void handleIncomingMessage(Message message) {
        Platform.runLater(() -> {
            if (!isCurrentConversationMessage(message)) {
                return;
            }
            // Nếu message đã tồn tại, reload toàn bộ để đảm bảo sync
            if (messageExists(message.getMessageId())) {
                logger.debug("Message {} already exists, reloading", message.getMessageId());
                reloadCurrentMessages();
                return;
            }

            logger.debug("Adding new message {} to conversation", message.getMessageId());
            // Thêm message mới vào cuối list và UI
            currentMessages.add(message);
            // Không cập nhật currentOffset vì offset chỉ dùng cho pagination (load tin cũ)
            showEmptyState(false);
            if (hasMore && !messagesContainer.getChildren().contains(loadMoreButton)) {
                updateLoadMoreButtonVisibility();
            }
            messagesContainer.getChildren().add(MessageViewFactory.createMessageNode(
                    message,
                    currentUserSupplier.get(),
                    timestampFormatter,
                    messageActionHandler
            ));
            scrollToBottomDelayed();

            User selectedContact = selectedContactSupplier.get();
            if (selectedContact != null && message.getSenderId() == selectedContact.getUserId()) {
                markPrivateChatAsRead();
            }
        });
    }

    public void handleMessagesMarkedAsRead(int readerId, int senderId) {
        User currentUser = currentUserSupplier.get();
        User selectedContact = selectedContactSupplier.get();
        if (currentUser == null || selectedContact == null) {
            return;
        }
        if (currentUser.getUserId() == senderId && selectedContact.getUserId() == readerId) {
            reloadCurrentMessages();
        }
    }

    public void handleMessageUpdated(Message message) {
        if (isCurrentConversationMessage(message)) {
            reloadCurrentMessages();
        }
    }

    public void handleMessageDeleted(int messageId) {
        if (messageExists(messageId)) {
            reloadCurrentMessages();
        }
    }

    public void displaySystemMessage(String message) {
        Platform.runLater(() -> {
            showEmptyState(false);
            messagesContainer.getChildren().add(buildSystemMessageNode(message));
            scrollToBottom();
        });
    }

    private List<Message> fetchMessages(int offset, int limit) throws RemoteException {
        User currentUser = currentUserSupplier.get();
        User contact = selectedContactSupplier.get();
        Group group = selectedGroupSupplier.get();
        if (currentUser == null) {
            return new ArrayList<>();
        }
        if (contact != null) {
            return clientRMI.getPrivateMessages(currentUser.getUserId(), contact.getUserId(), limit, offset);
        } else if (group != null) {
            return clientRMI.getGroupMessages(group.getGroupId(), limit, offset);
        }
        return new ArrayList<>();
    }

    private void reloadCurrentMessages() {
        final int versionSnapshot = conversationVersion;
        final int currentSize = currentMessages.size();
        new Thread(() -> {
            try {
                // Load lại từ đầu với limit đủ lớn để đảm bảo có đủ tin mới
                final int reloadLimit = currentSize > 0 ? currentSize + 10 : Math.max(pageSize, 20);
                logger.debug("Reloading messages with limit={}", reloadLimit);
                List<Message> refreshed = fetchMessages(0, reloadLimit);
                Platform.runLater(() -> {
                    if (!isCurrentVersion(versionSnapshot)) {
                        return;
                    }
                    logger.debug("Reloaded {} messages", refreshed.size());
                    currentMessages.clear();
                    currentMessages.addAll(refreshed);
                    currentOffset = refreshed.size();
                    // Kiểm tra xem có còn tin cũ hơn không
                    hasMore = refreshed.size() >= reloadLimit;
                    rebuildMessageList();
                    scrollToBottomDelayed();
                    if (selectedContactSupplier.get() != null) {
                        markPrivateChatAsRead();
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to reload messages", e);
            }
        }).start();
    }

    public void refreshCurrentConversation() {
        reloadCurrentMessages();
    }

    private void rebuildMessageList() {
        messagesContainer.getChildren().clear();
        if (currentMessages.isEmpty()) {
            showEmptyState(true);
            return;
        }

        showEmptyState(false);
        
        // Thêm load more button ở đầu nếu còn tin cũ hơn
        if (hasMore) {
            messagesContainer.getChildren().add(loadMoreButton);
        }
        
        // Render tất cả messages theo thứ tự chronological (cũ → mới)
        // currentMessages đã được sắp xếp: tin cũ nhất ở index 0, tin mới nhất ở cuối
        // Render theo đúng thứ tự: tin cũ ở trên, tin mới ở dưới
        for (Message message : currentMessages) {
            Node node = MessageViewFactory.createMessageNode(
                    message,
                    currentUserSupplier.get(),
                    timestampFormatter,
                    messageActionHandler
            );
            messagesContainer.getChildren().add(node);
        }
        
        logger.debug("Rebuilt message list: {} messages rendered", currentMessages.size());
        // Không scroll ở đây, để caller quyết định khi nào scroll
    }

    private void addMessagesAtTop(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        
        // Đảm bảo load more button ở đầu
        if (hasMore && !messagesContainer.getChildren().contains(loadMoreButton)) {
            messagesContainer.getChildren().add(0, loadMoreButton);
        }
        
        // Insert messages ngay sau load more button
        // messages đã được sắp xếp (cũ → mới), insert theo đúng thứ tự
        int insertIndex = hasMore ? 1 : 0;
        for (Message message : messages) {
            Node node = MessageViewFactory.createMessageNode(
                    message,
                    currentUserSupplier.get(),
                    timestampFormatter,
                    messageActionHandler
            );
            messagesContainer.getChildren().add(insertIndex++, node);
        }
        
        logger.debug("Added {} messages at top", messages.size());
    }

    private void updateLoadMoreButtonVisibility() {
        if (hasMore) {
            if (!messagesContainer.getChildren().contains(loadMoreButton)) {
                messagesContainer.getChildren().add(0, loadMoreButton);
            }
        } else {
            messagesContainer.getChildren().remove(loadMoreButton);
        }
    }

    private void showEmptyState(boolean visible) {
        if (visible) {
            if (!messagesContainer.getChildren().contains(emptyStateLabel)) {
                messagesContainer.getChildren().add(emptyStateLabel);
            }
            emptyStateLabel.setVisible(true);
            emptyStateLabel.setManaged(true);
        } else {
            emptyStateLabel.setVisible(false);
            emptyStateLabel.setManaged(false);
            messagesContainer.getChildren().remove(emptyStateLabel);
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }
    
    private void scrollToBottomDelayed() {
        // Scroll nhiều lần để đảm bảo scroll xuống cuối sau khi layout xong
        Platform.runLater(() -> {
            messagesScrollPane.setVvalue(1.0);
            Platform.runLater(() -> {
                messagesScrollPane.setVvalue(1.0);
                Platform.runLater(() -> {
                    messagesScrollPane.setVvalue(1.0);
                    // Đợi thêm một chút để layout hoàn tất
                    new Thread(() -> {
                        try {
                            Thread.sleep(100); // Tăng delay lên 100ms
                            Platform.runLater(() -> {
                                messagesScrollPane.setVvalue(1.0);
                                logger.debug("Scrolled to bottom, vvalue={}", messagesScrollPane.getVvalue());
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
            });
        });
    }

    private boolean isCurrentVersion(int versionSnapshot) {
        return versionSnapshot == conversationVersion;
    }

    private boolean isCurrentConversationMessage(Message message) {
        User currentUser = currentUserSupplier.get();
        if (currentUser == null || message == null) {
            return false;
        }
        User selectedContact = selectedContactSupplier.get();
        if (selectedContact != null) {
            Integer receiverId = message.getReceiverId();
            if (receiverId == null) {
                return false;
            }
            int selfId = currentUser.getUserId();
            int contactId = selectedContact.getUserId();
            return (message.getSenderId() == contactId && receiverId == selfId) ||
                   (message.getSenderId() == selfId && receiverId == contactId);
        }
        Group selectedGroup = selectedGroupSupplier.get();
        if (selectedGroup != null) {
            Integer groupId = message.getGroupId();
            return groupId != null && groupId == selectedGroup.getGroupId();
        }
        return false;
    }

    private boolean messageExists(int messageId) {
        return currentMessages.stream().anyMatch(msg -> msg.getMessageId() == messageId);
    }

    private void markPrivateChatAsRead() {
        User currentUser = currentUserSupplier.get();
        User selectedContact = selectedContactSupplier.get();
        if (currentUser == null || selectedContact == null) {
            return;
        }
        new Thread(() -> {
            try {
                clientRMI.markMessagesAsRead(currentUser.getUserId(), selectedContact.getUserId());
            } catch (Exception e) {
                logger.error("Failed to mark messages as read", e);
            }
        }).start();
    }

    private Node buildSystemMessageNode(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("system-message");
        VBox wrapper = new VBox(label);
        wrapper.getStyleClass().add("message-wrapper");
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }
}

