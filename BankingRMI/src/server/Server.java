package server;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
    private static Registry registry;
    private static BankingImpl bankingService;

    public static void main(String[] args) {
        try {
            // Tạo đối tượng implement
            bankingService = new BankingImpl();

            // Khởi tạo RMI Registry tại port 1099 (port RMI mặc định)
            // Dùng createRegistry để đảm bảo registry được khởi tạo
            try {
                registry = LocateRegistry.createRegistry(1099);
                System.out.println("RMI Registry created on port 1099");
            } catch (RemoteException e) {
                // Registry có thể đã tồn tại, thử lấy lại
                System.out.println("Registry might already exist, trying to get it...");
                registry = LocateRegistry.getRegistry(1099);
            }

            // Đăng ký đối tượng với một tên
            try {
                registry.bind("BankingService", bankingService);
                System.out.println("BankingService bound successfully");
            } catch (AlreadyBoundException e) {
                // Nếu đã bind rồi, unbind và bind lại
                System.out.println("BankingService already bound, rebinding...");
                registry.unbind("BankingService");
                registry.bind("BankingService", bankingService);
            }

            System.out.println(">>> Server is running on port 1099...");
            System.out.println(">>> Press Ctrl+C to stop the server");

            // Đăng ký shutdown hook để cleanup khi server tắt
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n>>> Shutting down server...");
                try {
                    if (registry != null && bankingService != null) {
                        registry.unbind("BankingService");
                        System.out.println("BankingService unbound");
                    }
                } catch (Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
                System.out.println(">>> Server stopped");
            }));

            // Giữ server chạy
            Thread.currentThread().join();

        } catch (RemoteException e) {
            System.err.println("Remote error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}