package server;

import common.IBanking;
import common.IClientCallback;
import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class BankingImpl extends UnicastRemoteObject implements IBanking {

    // Đường dẫn file dữ liệu
    private static final String DATA_FILE = "accounts.txt";

    // --- CƠ SỞ DỮ LIỆU "IN-MEMORY" ---
    // Key: STK (String), Value: Số dư (Double)
    // Dùng ConcurrentHashMap để thread-safe khi nhiều client truy cập đồng thời
    private final Map<String, Double> accounts;

    // Key: STK (String), Value: Đối tượng callback
    // Dùng ConcurrentHashMap để thread-safe
    private final Map<String, IClientCallback> clientMap;

    /**
     * Constructor: Khởi tạo dữ liệu từ file
     */
    public BankingImpl() throws RemoteException {
        super();
        clientMap = new ConcurrentHashMap<>();
        accounts = new ConcurrentHashMap<>();

        // Gọi hàm đọc dữ liệu từ file
        loadAccounts();
    }

    // --- CÁC HÀM XỬ LÝ FILE (ĐỌC/GHI) ---

    /**
     * Đọc dữ liệu từ file "accounts.txt" và nạp vào Map
     */
    private void loadAccounts() {
        // Dùng try-with-resources để tự động đóng file
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            System.out.println("Loading accounts from file...");
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String accountId = parts[0].trim();
                    double balance = Double.parseDouble(parts[1].trim());
                    accounts.put(accountId, balance);
                    System.out.println("  Loaded: " + accountId + " - " + balance);
                }
            }
            System.out.println("Accounts loaded successfully.");
        } catch (FileNotFoundException e) {
            System.err.println("WARNING: Data file '" + DATA_FILE + "' not found. Starting with empty data.");
            // File không tồn tại (có thể là lần chạy đầu), không cần làm gì
        } catch (IOException e) {
            System.err.println("ERROR: Failed to read data file: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("ERROR: Invalid data format in file: " + e.getMessage());
        }
    }

    /**
     * Ghi toàn bộ dữ liệu từ Map vào file "accounts.txt"
     * Dùng "synchronized" để tránh lỗi khi nhiều client ghi file cùng lúc
     */
    private synchronized void saveAccounts() {
        // Dùng try-with-resources để tự động đóng file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
            System.out.println("Saving accounts to file...");
            for (Map.Entry<String, Double> entry : accounts.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue());
                writer.newLine(); // Thêm dòng mới
            }
            System.out.println("Accounts saved successfully.");
        } catch (IOException e) {
            System.err.println("ERROR: Failed to save data file: " + e.getMessage());
        }
    }

    // --- CÁC HÀM NGHIỆP VỤ (Đã có logic) ---

    @Override
    public String checkBalance(String accountId) throws RemoteException {
        // Validate input
        if (accountId == null || accountId.trim().isEmpty()) {
            return "Tài khoản không hợp lệ";
        }

        Double balance = accounts.get(accountId.trim());
        if (balance == null) {
            return "Tài khoản không tồn tại";
        }
        return String.format("%,.0f VND", balance);
    }

    @Override
    public boolean deposit(String accountId, double amount) throws RemoteException {
        // Validate input
        if (accountId == null || accountId.trim().isEmpty() || amount <= 0) {
            return false;
        }

        String accountIdTrimmed = accountId.trim();
        Double currentBalance = accounts.get(accountIdTrimmed);
        if (currentBalance == null) {
            return false;
        }

        accounts.put(accountIdTrimmed, currentBalance + amount);
        saveAccounts(); // Lưu lại file sau khi nạp

        System.out.println("DEPOSIT: " + accountIdTrimmed + " nạp " + amount);
        return true;
    }

    @Override
    public boolean withdraw(String accountId, double amount) throws RemoteException {
        // Validate input
        if (accountId == null || accountId.trim().isEmpty() || amount <= 0) {
            return false;
        }

        String accountIdTrimmed = accountId.trim();
        Double currentBalance = accounts.get(accountIdTrimmed);
        if (currentBalance == null) {
            return false;
        }

        if (currentBalance < amount) {
            System.out.println("WITHDRAW FAILED (No balance): " + accountIdTrimmed);
            return false;
        }

        accounts.put(accountIdTrimmed, currentBalance - amount);
        saveAccounts(); // Lưu lại file sau khi rút

        System.out.println("WITHDRAW: " + accountIdTrimmed + " rút " + amount);
        return true;
    }

    @Override
    public boolean transfer(String fromAccountId, String toAccountId, double amount) throws RemoteException {
        // Validate input
        if (fromAccountId == null || fromAccountId.trim().isEmpty() ||
                toAccountId == null || toAccountId.trim().isEmpty() ||
                amount <= 0) {
            return false;
        }

        String fromAccountIdTrimmed = fromAccountId.trim();
        String toAccountIdTrimmed = toAccountId.trim();

        // Kiểm tra chuyển cho chính mình
        if (fromAccountIdTrimmed.equals(toAccountIdTrimmed)) {
            System.out.println("TRANSFER FAILED (Same account): " + fromAccountIdTrimmed);
            return false;
        }

        Double fromBalance = accounts.get(fromAccountIdTrimmed);
        Double toBalance = accounts.get(toAccountIdTrimmed);

        if (fromBalance == null || toBalance == null) {
            return false;
        }

        if (fromBalance < amount) {
            System.out.println("TRANSFER FAILED (No balance): " + fromAccountIdTrimmed);
            return false;
        }

        // Cập nhật số dư atomic
        accounts.put(fromAccountIdTrimmed, fromBalance - amount);
        accounts.put(toAccountIdTrimmed, toBalance + amount);

        saveAccounts(); // Lưu lại file sau khi chuyển

        System.out.println("TRANSFER: " + fromAccountIdTrimmed + " -> " + toAccountIdTrimmed + ": " + amount);

        // --- XỬ LÝ CALLBACK (gửi thông báo cho người nhận) ---
        IClientCallback recipientClient = clientMap.get(toAccountIdTrimmed);
        if (recipientClient != null) {
            try {
                String message = String.format("Bạn vừa nhận được %,.0f VND từ TK %s", amount, fromAccountIdTrimmed);
                recipientClient.notify(message);
            } catch (RemoteException e) {
                // Client đã disconnect, remove khỏi map
                clientMap.remove(toAccountIdTrimmed);
                System.out.println("Client " + toAccountIdTrimmed + " disconnected, removed from callback map.");
            }
        }
        return true;
    }

    @Override
    public void registerClient(String accountId, IClientCallback client) throws RemoteException {
        if (accountId == null || accountId.trim().isEmpty() || client == null) {
            throw new RemoteException("Invalid registration parameters");
        }

        String accountIdTrimmed = accountId.trim();
        clientMap.put(accountIdTrimmed, client);
        System.out.println("Client " + accountIdTrimmed + " has registered for notifications.");
    }

    /**
     * Hủy đăng ký client (có thể gọi khi client disconnect)
     */
    public void unregisterClient(String accountId) {
        if (accountId != null) {
            clientMap.remove(accountId.trim());
            System.out.println("Client " + accountId.trim() + " has unregistered.");
        }
    }
}