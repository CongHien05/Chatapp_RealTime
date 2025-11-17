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
        this.loadMoreButton.setOnAction(e -> loadMoreMessages());

        this.emptyStateLabel = new Label("Chưa có tin nhắn");
        this.emptyStateLabel.getStyleClass().add("empty-message");
    }

    public void loadConversation(User contact, Group group) {
        Platform.runLater(() -> {
            currentMessages.clear();
            currentOffset = 0;
            isLoading = false;
            hasMore = true;
            messagesContainer.getChildren().clear();
            updateLoadMoreButtonVisibility();
            showEmptyState(true);
        });
        loadMoreMessages();
    }

    public void loadMoreMessages() {
        if (isLoading) {
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
        new Thread(() -> {
            try {
                List<Message> fetched = fetchMessages(requestOffset, pageSize);
                Platform.runLater(() -> {
                    boolean initialFetch = requestOffset == 0;
                    if (fetched.size() < pageSize) {
                        hasMore = false;
                    }
                    currentOffset += fetched.size();
                    currentMessages.addAll(0, fetched);

                    if (currentMessages.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        addMessagesAtTop(fetched);
                    }

                    if (initialFetch && !currentMessages.isEmpty()) {
                        scrollToBottom();
                        if (selectedContactSupplier.get() != null) {
                            markPrivateChatAsRead();
                        }
                    }

                    isLoading = false;
                    loadMoreButton.setText("Tải thêm");
                    loadMoreButton.setDisable(false);
                    updateLoadMoreButtonVisibility();
                });
            } catch (Exception e) {
                logger.error("Failed to load more messages", e);
                Platform.runLater(() -> {
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
            if (messageExists(message.getMessageId())) {
                reloadCurrentMessages();
                return;
            }

            currentMessages.add(message);
            currentOffset = currentMessages.size();
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
            scrollToBottom();

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
        new Thread(() -> {
            try {
                List<Message> refreshed = fetchMessages(0, Math.max(currentMessages.size(), pageSize));
                Platform.runLater(() -> {
                    currentMessages.clear();
                    currentMessages.addAll(refreshed);
                    currentOffset = currentMessages.size();
                    rebuildMessageList();
                    if (selectedContactSupplier.get() != null) {
                        markPrivateChatAsRead();
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to reload messages", e);
            }
        }).start();
    }

    private void rebuildMessageList() {
        messagesContainer.getChildren().clear();
        updateLoadMoreButtonVisibility();
        if (currentMessages.isEmpty()) {
            showEmptyState(true);
            return;
        }

        showEmptyState(false);
        if (hasMore && !messagesContainer.getChildren().contains(loadMoreButton)) {
            messagesContainer.getChildren().add(loadMoreButton);
        }
        int insertIndex = messagesContainer.getChildren().contains(loadMoreButton) ? 1 : 0;
        for (Message message : currentMessages) {
            Node node = MessageViewFactory.createMessageNode(
                    message,
                    currentUserSupplier.get(),
                    timestampFormatter,
                    messageActionHandler
            );
            messagesContainer.getChildren().add(insertIndex++, node);
        }
        scrollToBottom();
    }

    private void addMessagesAtTop(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        if (hasMore && !messagesContainer.getChildren().contains(loadMoreButton)) {
            messagesContainer.getChildren().add(0, loadMoreButton);
        }
        int insertIndex = messagesContainer.getChildren().contains(loadMoreButton) ? 1 : 0;
        for (Message message : messages) {
            Node node = MessageViewFactory.createMessageNode(
                    message,
                    currentUserSupplier.get(),
                    timestampFormatter,
                    messageActionHandler
            );
            messagesContainer.getChildren().add(insertIndex++, node);
        }
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
        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
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

