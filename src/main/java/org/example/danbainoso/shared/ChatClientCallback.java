package org.example.danbainoso.shared;

import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.shared.models.Friendship;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChatClientCallback extends Remote {
    void onMessageReceived(Message message) throws RemoteException;
    void onUserStatusChanged(int userId, User.UserStatus status) throws RemoteException;
    void onUserJoinedGroup(int groupId, User user) throws RemoteException;
    void onUserLeftGroup(int groupId, int userId) throws RemoteException;
    void onMessagesMarkedAsRead(int readerId, int senderId) throws RemoteException;
    void onMessageUpdated(Message message) throws RemoteException;
    void onMessageDeleted(int messageId) throws RemoteException;
    void onFriendRequestReceived(Friendship friendship) throws RemoteException;
}

