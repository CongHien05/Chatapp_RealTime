package org.example.danbainoso.client.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;

import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.function.Function;

/**
 * Helper class to build message nodes for the chat view.
 */
public class MessageViewFactory {

    public interface MessageActionHandler {
        boolean editMessage(int messageId, String newContent) throws RemoteException;
        boolean deleteMessage(int messageId) throws RemoteException;
        void showAlert(String message);
    }

    private MessageViewFactory() {
    }

    public static Node createMessageNode(Message message,
                                         User currentUser,
                                         Function<Timestamp, String> timestampFormatter,
                                         MessageActionHandler actions) {
        boolean isOwnMessage = currentUser != null && message.getSenderId() == currentUser.getUserId();

        String senderName = isOwnMessage ? "Bạn" :
                (message.getSenderName() != null ? message.getSenderName() : "Người dùng");

        Label senderLabel = null;
        if (!isOwnMessage) {
            senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("message-sender");
        }

        String contentText = message.isDeleted() ? "Tin nhắn đã bị xóa" : message.getContent();
        if (message.isEdited() && !message.isDeleted()) {
            contentText += " (đã chỉnh sửa)";
        }
        Label contentLabel = new Label(contentText);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(400);
        contentLabel.getStyleClass().add("message-text");

        String statusIcon = "";
        if (isOwnMessage) {
            statusIcon = message.isRead() ? " ✓✓" : " ✓";
        }
        Label timestampLabel = new Label(timestampFormatter.apply(message.getCreatedAt()) + statusIcon);
        timestampLabel.getStyleClass().add("message-timestamp");

        VBox bubble = new VBox();
        bubble.setSpacing(4);
        bubble.getStyleClass().addAll("message-bubble", isOwnMessage ? "message-bubble-own" : "message-bubble-other");
        if (senderLabel != null) {
            bubble.getChildren().add(senderLabel);
        }
        bubble.getChildren().addAll(contentLabel, timestampLabel);
        bubble.setMaxWidth(420);

        HBox wrapper = new HBox(bubble);
        wrapper.getStyleClass().add("message-wrapper");
        wrapper.setAlignment(isOwnMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (isOwnMessage && !message.isDeleted()) {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem editItem = new MenuItem("Sửa");
            MenuItem deleteItem = new MenuItem("Xóa");

            editItem.setOnAction(e -> handleEdit(actions, message));
            deleteItem.setOnAction(e -> handleDelete(actions, message));

            contextMenu.getItems().addAll(editItem, deleteItem);
            bubble.setOnContextMenuRequested(evt -> contextMenu.show(bubble, evt.getScreenX(), evt.getScreenY()));
        } else {
            bubble.setOnContextMenuRequested(null);
        }

        return wrapper;
    }

    private static void handleEdit(MessageActionHandler actions, Message message) {
        TextInputDialog dialog = new TextInputDialog(message.getContent());
        dialog.setTitle("Sửa tin nhắn");
        dialog.setHeaderText(null);
        dialog.setContentText("Nội dung mới:");
        dialog.showAndWait().ifPresent(newText -> {
            String trimmed = newText.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            try {
                boolean ok = actions.editMessage(message.getMessageId(), trimmed);
                if (!ok) {
                    actions.showAlert("Không thể sửa tin nhắn này.");
                }
            } catch (RemoteException e) {
                actions.showAlert("Có lỗi khi sửa tin nhắn: " + e.getMessage());
            }
        });
    }

    private static void handleDelete(MessageActionHandler actions, Message message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xóa tin nhắn");
        alert.setHeaderText(null);
        alert.setContentText("Bạn có chắc muốn xóa tin nhắn này?");
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                try {
                    boolean ok = actions.deleteMessage(message.getMessageId());
                    if (!ok) {
                        actions.showAlert("Không thể xóa tin nhắn này.");
                    }
                } catch (RemoteException e) {
                    actions.showAlert("Có lỗi khi xóa tin nhắn: " + e.getMessage());
                }
            }
        });
    }
}

