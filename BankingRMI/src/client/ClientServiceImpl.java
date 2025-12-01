package client;

import common.IBanking;
import common.IClientCallback;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Class xử lý logic nghiệp vụ và giao tiếp với RMI Server
 */
public class ClientServiceImpl extends UnicastRemoteObject implements IClientCallback {

    private IBanking bankingService;
    private final String accountId;
    private ClientGUI gui;

    public ClientServiceImpl(String accountId) throws RemoteException {
        super();
        this.accountId = accountId;
    }

    /**
     * Gắn GUI vào service
     */
    public void setGUI(ClientGUI gui) {
        this.gui = gui;
    }

    /**
     * Callback từ Server - Nhận thông báo
     */
    @Override
    public void notify(String message) throws RemoteException {
        System.out.println("THÔNG BÁO: " + message);

        if (gui != null) {
            gui.showNotification(message);
            // Tự động vấn tin sau khi nhận thông báo
            checkBalance();
        }
    }

    /**
     * Kết nối tới RMI Server
     */
    public void connectToServer() throws Exception {

        bankingService = (IBanking) Naming.lookup("BankingService");
        // nếu bạn muốn chia sẻ trên mạng thì cần sửa lại "BankingService" thành "rmi:// địa chỉ ip của registry /srvobj"
//         bankingService = (IBanking) Naming.lookup("rmi://192.168.1.5:1099/BankingService");

        bankingService.registerClient(accountId, this);

        if (gui != null) {
            gui.appendLog("Kết nối server thành công. Đã đăng ký nhận thông báo cho TK: " + accountId);
        }

        // Tự động vấn tin khi kết nối
        checkBalance();
    }

    /**
     * Vấn tin số dư
     */
    public void checkBalance() {
        if (bankingService == null) {
            if (gui != null) {
                gui.showError("Chưa kết nối tới server. Vui lòng đợi...");
            }
            return;
        }

        try {
            String balance = bankingService.checkBalance(accountId);
            if (gui != null) {
                gui.updateBalance(balance);
                gui.appendLog("Vấn tin TK " + accountId + ": " + balance);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            if (gui != null) {
                gui.showError("Lỗi khi vấn tin: " + e.getMessage());
            }
        }
    }

    /**
     * Nạp tiền
     */
    public void deposit(double amount) {
        if (bankingService == null) {
            if (gui != null) {
                gui.showError("Chưa kết nối tới server. Vui lòng đợi...");
            }
            return;
        }

        if (gui == null) {
            return;
        }

        try {
            if (amount <= 0) {
                gui.showWarning("Số tiền phải lớn hơn 0");
                return;
            }

            boolean success = bankingService.deposit(accountId, amount);

            if (success) {
                gui.appendLog("Nạp tiền " + String.format("%,.0f", amount) + " thành công.");
                checkBalance();
            } else {
                gui.appendLog("Nạp tiền " + String.format("%,.0f", amount) + " thất bại.");
                gui.showWarning("Nạp tiền thất bại. Kiểm tra lại tài khoản hoặc số tiền.");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            gui.showError("Lỗi khi nạp tiền: " + e.getMessage());
        }
    }

    /**
     * Rút tiền
     */
    public void withdraw(double amount) {
        if (bankingService == null) {
            if (gui != null) {
                gui.showError("Chưa kết nối tới server. Vui lòng đợi...");
            }
            return;
        }

        if (gui == null) {
            return;
        }

        try {
            if (amount <= 0) {
                gui.showWarning("Số tiền phải lớn hơn 0");
                return;
            }

            boolean success = bankingService.withdraw(accountId, amount);

            if (success) {
                gui.appendLog("Rút tiền " + String.format("%,.0f", amount) + " thành công.");
                checkBalance();
            } else {
                gui.appendLog("Rút tiền " + String.format("%,.0f", amount) + " thất bại (không đủ số dư).");
                gui.showWarning("Không đủ số dư để rút");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            gui.showError("Lỗi khi rút tiền: " + e.getMessage());
        }
    }

    /**
     * Chuyển khoản
     */
    public void transfer(String toAccount, double amount) {
        if (bankingService == null) {
            if (gui != null) {
                gui.showError("Chưa kết nối tới server. Vui lòng đợi...");
            }
            return;
        }

        if (gui == null) {
            return;
        }

        try {
            if (toAccount == null || toAccount.trim().isEmpty()) {
                gui.showWarning("Vui lòng nhập tài khoản nhận");
                return;
            }
            if (amount <= 0) {
                gui.showWarning("Số tiền phải lớn hơn 0");
                return;
            }

            String toAccountTrimmed = toAccount.trim();
            if (toAccountTrimmed.equals(accountId)) {
                gui.showWarning("Bạn không thể chuyển tiền cho chính mình");
                return;
            }

            boolean success = bankingService.transfer(accountId, toAccountTrimmed, amount);

            if (success) {
                gui.appendLog("Chuyển " + String.format("%,.0f", amount) + " tới TK " + toAccountTrimmed + " thành công.");
                checkBalance();
            } else {
                gui.appendLog("Chuyển tiền thất bại (lỗi logic/không đủ số dư).");
                gui.showWarning("Giao dịch thất bại (Không đủ số dư hoặc TK nhận sai)");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            gui.showError("Lỗi khi chuyển khoản: " + e.getMessage());
        }
    }

    public String getAccountId() {
        return accountId;
    }
}