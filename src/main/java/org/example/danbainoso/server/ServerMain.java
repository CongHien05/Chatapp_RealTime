package org.example.danbainoso.server;

import org.example.danbainoso.database.DatabaseConnection;
import org.example.danbainoso.shared.ChatService;
import org.example.danbainoso.shared.VideoService;
import org.example.danbainoso.utils.Config;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    private static final Logger logger = LoggerUtil.getLogger(ServerMain.class);
    
    public static void main(String[] args) {
        try {
            // Initialize database connection
            logger.info("Initializing database connection...");
            if (!DatabaseConnection.isHealthy()) {
                logger.error("Database connection failed. Please check your database configuration.");
                System.exit(1);
            }
            logger.info("Database connection established successfully");
            
            // Create service implementations
            // Note: ChatServiceImpl and VideoServiceImpl extend UnicastRemoteObject
            // which automatically exports them in the constructor, so we don't need to export again
            logger.info("Creating service implementations...");
            ChatService chatService = new ChatServiceImpl();
            VideoService videoService = new VideoServiceImpl();
            
            // Create or get RMI registry
            String host = Config.getServerHost();
            int port = Config.getServerRmiPort();

            // Đặt hostname cho RMI khi chia sẻ server qua mạng LAN/Internet
            // - Nếu chỉ chạy local: để server.host=localhost (mặc định)
            // - Nếu cho máy khác kết nối: sửa server.host trong config.properties
            //   thành địa chỉ IP của máy chạy server (ví dụ: 192.168.1.5),
            //   RMI sẽ dùng IP này khi sinh stub cho callback về client.
            System.setProperty("java.rmi.server.hostname", host);

            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(host, port);
                registry.list(); // Test if registry exists
                logger.info("Using existing RMI registry at {}:{}", host, port);
            } catch (Exception e) {
                logger.info("Creating new RMI registry at {}:{}", host, port);
                registry = LocateRegistry.createRegistry(port);
            }
            
            // Bind services to registry (services are already exported by UnicastRemoteObject)
            registry.rebind("ChatService", chatService);
            registry.rebind("VideoService", videoService);
            
            logger.info("=========================================");
            logger.info("Chat Server Started Successfully!");
            logger.info("RMI Registry: {}:{}", host, port);
            logger.info("ChatService bound: ChatService");
            logger.info("VideoService bound: VideoService");
            logger.info("=========================================");
            logger.info("Server is running... Press Ctrl+C to stop");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down server...");
                try {
                    DatabaseConnection.close();
                    logger.info("Server shutdown complete");
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
            }));
            
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
}
