package org.example.danbainoso.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.danbainoso.client.ClientMain;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.util.function.Consumer;

public class GroupController {
    private static final Logger logger = LoggerUtil.getLogger(GroupController.class);
    
    @FXML
    private TextField groupNameField;
    
    @FXML
    private TextArea descriptionField;
    
    @FXML
    private Button createButton;
    
    @FXML
    private Button cancelButton;
    
    private ClientRMI clientRMI;
    private User currentUser;
    private Consumer<Group> onGroupCreated;
    
    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
    }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setOnGroupCreated(Consumer<Group> onGroupCreated) {
        this.onGroupCreated = onGroupCreated;
    }
    
    @FXML
    private void handleCreate() {
        String groupName = groupNameField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (groupName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập tên nhóm!");
            return;
        }
        
        try {
            Group group = new Group(groupName, description, currentUser.getUserId());
            Group createdGroup = clientRMI.createGroup(group);
            if (createdGroup != null) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Tạo nhóm thành công!");
                if (onGroupCreated != null) {
                    Platform.runLater(() -> onGroupCreated.accept(createdGroup));
                }
                closeWindow();
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tạo nhóm!");
            }
        } catch (Exception e) {
            logger.error("Failed to create group", e);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tạo nhóm: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        closeWindow();
    }
    
    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
