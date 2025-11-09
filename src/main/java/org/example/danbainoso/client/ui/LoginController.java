package org.example.danbainoso.client.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.danbainoso.client.ClientMain;
import org.example.danbainoso.client.ClientRMI;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.io.IOException;

public class LoginController {
    private static final Logger logger = LoggerUtil.getLogger(LoginController.class);
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Button registerButton;
    
    private ClientRMI clientRMI;
    
    @FXML
    public void initialize() {
        clientRMI = ClientMain.getClientRMI();
    }
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }
        
        try {
            User user = clientRMI.login(username, password);
            if (user != null) {
                logger.info("User logged in: {}", username);
                openChatWindow(user);
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi đăng nhập", "Tên đăng nhập hoặc mật khẩu không đúng!");
            }
        } catch (Exception e) {
            logger.error("Login failed", e);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể kết nối đến server: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }
        
        if (password.length() < 6) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }
        
        try {
            User newUser = new User(username, password, username + "@example.com", username);
            boolean success = clientRMI.register(newUser);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                passwordField.clear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", "Tên đăng nhập hoặc email đã tồn tại! Vui lòng chọn tên khác.");
            }
        } catch (Exception e) {
            logger.error("Registration failed", e);
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Duplicate entry")) {
                showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", "Tên đăng nhập hoặc email đã tồn tại! Vui lòng chọn tên khác.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể kết nối đến server: " + (errorMessage != null ? errorMessage : "Unknown error"));
            }
        }
    }
    
    private void openChatWindow(User user) {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/danbainoso/client/ui/chat.fxml"));
            Parent root = loader.load();
            
            ChatController chatController = loader.getController();
            chatController.setCurrentUser(user);
            
            Scene scene = new Scene(root, 1000, 700);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            
            Stage chatStage = new Stage();
            chatStage.setTitle("DanBaiNoSo Chat - " + user.getUsername());
            chatStage.setScene(scene);
            chatStage.setOnCloseRequest(e -> {
                try {
                    clientRMI.updateUserStatus(User.UserStatus.OFFLINE);
                    clientRMI.unregisterCallbacks();
                } catch (Exception ex) {
                    logger.error("Failed to update status on close", ex);
                }
            });
            
            chatStage.show();
            currentStage.close();
        } catch (IOException e) {
            logger.error("Failed to open chat window", e);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở cửa sổ chat: " + e.getMessage());
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
