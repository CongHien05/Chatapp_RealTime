package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IBanking extends Remote {
    // Các chức năng cơ bản
    String checkBalance(String accountId) throws RemoteException;
    boolean deposit(String accountId, double amount) throws RemoteException;
    boolean withdraw(String accountId, double amount) throws RemoteException;
    boolean transfer(String fromAccountId, String toAccountId, double amount) throws RemoteException;

    // ----- Phần cho Callback -----
    // Client sẽ gọi phương thức này để "đăng ký" nhận thông báo
    void registerClient(String accountId, IClientCallback client) throws RemoteException;
}