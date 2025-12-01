package client;

import javax.swing.*;
import java.awt.Font;
import java.rmi.RemoteException;

/**
 * Class quản lý giao diện GUI
 */
public class ClientGUI {

    private final ClientServiceImpl service;
    private JFrame frame;
    private JLabel lblBalance;
    private JTextField txtRecipient;
    private JTextField txtAmount;
    private JTextArea logArea;


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Hiển thị form đăng nhập
                String accountId = JOptionPane.showInputDialog(
                        null,
                        "Vui lòng nhập số tài khoản của bạn:",
                        "Đăng nhập eBanking",
                        JOptionPane.PLAIN_MESSAGE
                );

                if (accountId == null || accountId.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Bạn chưa nhập số tài khoản. Thoát chương trình.",
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                // 2. Khởi tạo Service
                ClientServiceImpl service = new ClientServiceImpl(accountId.trim());

                // 3. Khởi tạo và hiển thị GUI
                ClientGUI gui = new ClientGUI(service);
                gui.show();

                // 4. Kết nối tới server trên luồng nền
                new Thread(() -> {
                    try {
                        service.connectToServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                        gui.appendLog("Lỗi: Không thể kết nối tới server.");
                        gui.showError("Không thể kết nối tới server:\n" + e.getMessage());
                    }
                }).start();

            } catch (RemoteException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Không thể khởi tạo Client:\n" + e.getMessage(),
                        "Lỗi nghiêm trọng",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
    public ClientGUI(ClientServiceImpl service) {
        this.service = service;
        service.setGUI(this);
        initializeGUI();
    }

    /**
     * Khởi tạo giao diện
     */
    private void initializeGUI() {
        frame = new JFrame("eBanking Client - TK: " + service.getAccountId());
        frame.setBounds(100, 100, 600, 450);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(null);

        // --- Cột bên trái ---
        JLabel lblMyAccountTitle = new JLabel("Tài khoản gốc:");
        lblMyAccountTitle.setBounds(20, 20, 100, 25);
        frame.getContentPane().add(lblMyAccountTitle);

        JLabel lblMyAccount = new JLabel(service.getAccountId());
        lblMyAccount.setFont(new Font("Tahoma", Font.BOLD, 14));
        lblMyAccount.setBounds(130, 20, 150, 25);
        frame.getContentPane().add(lblMyAccount);

        JLabel lblBalanceTitle = new JLabel("Số dư:");
        lblBalanceTitle.setBounds(20, 60, 100, 25);
        frame.getContentPane().add(lblBalanceTitle);

        lblBalance = new JLabel("Chưa vấn tin");
        lblBalance.setFont(new Font("Tahoma", Font.BOLD, 14));
        lblBalance.setBounds(130, 60, 200, 25);
        frame.getContentPane().add(lblBalance);

        JLabel lblRecipient = new JLabel("Tài khoản nhận:");
        lblRecipient.setBounds(20, 100, 100, 25);
        frame.getContentPane().add(lblRecipient);

        txtRecipient = new JTextField();
        txtRecipient.setBounds(130, 100, 200, 25);
        frame.getContentPane().add(txtRecipient);

        JLabel lblAmount = new JLabel("Số tiền/ND:");
        lblAmount.setBounds(20, 140, 100, 25);
        frame.getContentPane().add(lblAmount);

        txtAmount = new JTextField();
        txtAmount.setBounds(130, 140, 200, 25);
        frame.getContentPane().add(txtAmount);

        // --- Cột bên phải (Nút bấm) ---
        JButton btnCheckBalance = new JButton("Vấn tin");
        btnCheckBalance.setBounds(400, 20, 160, 25);
        frame.getContentPane().add(btnCheckBalance);

        JButton btnDeposit = new JButton("Nạp tiền");
        btnDeposit.setBounds(400, 60, 160, 25);
        frame.getContentPane().add(btnDeposit);

        JButton btnWithdraw = new JButton("Rút tiền");
        btnWithdraw.setBounds(400, 100, 160, 25);
        frame.getContentPane().add(btnWithdraw);

        JButton btnTransfer = new JButton("Chuyển khoản");
        btnTransfer.setBounds(400, 140, 160, 25);
        frame.getContentPane().add(btnTransfer);

        // --- Khu vực Log ---
        JLabel lblLog = new JLabel("Hộp thông tin:");
        lblLog.setBounds(20, 180, 150, 25);
        frame.getContentPane().add(lblLog);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBounds(20, 210, 540, 180);
        frame.getContentPane().add(scrollPane);

        // --- Gắn sự kiện ---
        btnCheckBalance.addActionListener(e -> new Thread(() -> service.checkBalance()).start());

        btnDeposit.addActionListener(e -> {
            String amountStr = txtAmount.getText().trim();
            if (amountStr.isEmpty()) {
                showWarning("Vui lòng nhập số tiền");
                return;
            }
            new Thread(() -> {
                try {
                    double amount = Double.parseDouble(amountStr);
                    if (amount <= 0) {
                        showWarning("Số tiền phải lớn hơn 0");
                        return;
                    }
                    service.deposit(amount);
                } catch (NumberFormatException ex) {
                    showWarning("Số tiền không hợp lệ. Vui lòng nhập số.");
                }
            }).start();
        });

        btnWithdraw.addActionListener(e -> {
            String amountStr = txtAmount.getText().trim();
            if (amountStr.isEmpty()) {
                showWarning("Vui lòng nhập số tiền");
                return;
            }
            new Thread(() -> {
                try {
                    double amount = Double.parseDouble(amountStr);
                    if (amount <= 0) {
                        showWarning("Số tiền phải lớn hơn 0");
                        return;
                    }
                    service.withdraw(amount);
                } catch (NumberFormatException ex) {
                    showWarning("Số tiền không hợp lệ. Vui lòng nhập số.");
                }
            }).start();
        });

        btnTransfer.addActionListener(e -> {
            String recipient = txtRecipient.getText().trim();
            String amountStr = txtAmount.getText().trim();

            if (recipient.isEmpty()) {
                showWarning("Vui lòng nhập tài khoản nhận");
                return;
            }
            if (amountStr.isEmpty()) {
                showWarning("Vui lòng nhập số tiền");
                return;
            }

            new Thread(() -> {
                try {
                    double amount = Double.parseDouble(amountStr);
                    if (amount <= 0) {
                        showWarning("Số tiền phải lớn hơn 0");
                        return;
                    }
                    service.transfer(recipient, amount);
                } catch (NumberFormatException ex) {
                    showWarning("Số tiền không hợp lệ. Vui lòng nhập số.");
                }
            }).start();
        });
    }

    /**
     * Hiển thị frame
     */
    public void show() {
        frame.setVisible(true);
    }

    /**
     * Cập nhật số dư
     */
    public void updateBalance(String balance) {
        SwingUtilities.invokeLater(() -> lblBalance.setText(balance));
    }

    /**
     * Thêm log
     */
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    /**
     * Hiển thị thông báo từ server
     */
    public void showNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("THÔNG BÁO: " + message + "\n");
            JOptionPane.showMessageDialog(frame, message, "Thông báo từ Server", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /**
     * Hiển thị lỗi
     */
    public void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Lỗi", JOptionPane.ERROR_MESSAGE)
        );
    }

    /**
     * Hiển thị cảnh báo
     */
    public void showWarning(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Cảnh báo", JOptionPane.WARNING_MESSAGE)
        );
    }

    public JFrame getFrame() {
        return frame;
    }
}