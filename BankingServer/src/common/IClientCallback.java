package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClientCallback extends Remote {
    // Phương thức Server sẽ gọi để gửi thông báo cho Client
    void notify(String message) throws RemoteException;
}