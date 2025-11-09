package org.example.danbainoso.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

public class ClientMain extends Application {
    private static final Logger logger = LoggerUtil.getLogger(ClientMain.class);
    private static ClientRMI clientRMI;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            // Load login screen - try multiple ways to find resource
            java.net.URL fxmlUrl = getClass().getResource("/org/example/danbainoso/client/ui/login.fxml");
            if (fxmlUrl == null) {
                // Try with ClassLoader
                fxmlUrl = getClass().getClassLoader().getResource("org/example/danbainoso/client/ui/login.fxml");
            }
            if (fxmlUrl == null) {
                // Try loading from file system as fallback
                java.io.File fxmlFile = new java.io.File("src/main/resources/org/example/danbainoso/client/ui/login.fxml");
                if (fxmlFile.exists()) {
                    fxmlUrl = fxmlFile.toURI().toURL();
                    logger.info("Loading FXML from file system: {}", fxmlUrl);
                } else {
                    // Try from target/classes
                    fxmlFile = new java.io.File("target/classes/org/example/danbainoso/client/ui/login.fxml");
                    if (fxmlFile.exists()) {
                        fxmlUrl = fxmlFile.toURI().toURL();
                        logger.info("Loading FXML from target/classes: {}", fxmlUrl);
                    }
                }
            }
            if (fxmlUrl == null) {
                logger.error("Cannot find FXML file: /org/example/danbainoso/client/ui/login.fxml");
                logger.error("Current classpath: {}", System.getProperty("java.class.path"));
                logger.error("Current working directory: {}", System.getProperty("user.dir"));
                throw new java.io.FileNotFoundException("FXML file not found");
            }
            logger.info("Loading FXML from: {}", fxmlUrl);
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 400, 500);
            java.net.URL cssUrl = getClass().getResource("/css/style.css");
            if (cssUrl == null) {
                cssUrl = getClass().getClassLoader().getResource("css/style.css");
            }
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            
            primaryStage.setTitle("DanBaiNoSo Chat - Login");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
            
            logger.info("Client application started");
        } catch (Exception e) {
            logger.error("Failed to start client application", e);
            e.printStackTrace();
        }
    }
    
    @Override
    public void stop() {
        if (clientRMI != null) {
            clientRMI.unregisterCallbacks();
            if (clientRMI.getCurrentUser() != null) {
                try {
                    clientRMI.updateUserStatus(org.example.danbainoso.shared.models.User.UserStatus.OFFLINE);
                } catch (Exception e) {
                    logger.error("Failed to update user status on exit", e);
                }
            }
        }
        logger.info("Client application stopped");
    }
    
    public static void main(String[] args) {
        // Initialize RMI connection
        clientRMI = new ClientRMI();
        if (!clientRMI.connect()) {
            logger.error("Failed to connect to server. Please make sure the server is running.");
            System.exit(1);
        }
        
        launch(args);
    }
    
    public static ClientRMI getClientRMI() {
        return clientRMI;
    }
}
