package org.example.danbainoso.shared;

import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ChatService extends Remote {
    
    // User operations
    User login(String username, String password) throws RemoteException;
    boolean register(User user) throws RemoteException;
    boolean updateUserStatus(int userId, User.UserStatus status) throws RemoteException;
    List<User> searchUsers(String keyword) throws RemoteException;
    User getUserById(int userId) throws RemoteException;
    
    // Message operations
    Message sendMessage(Message message) throws RemoteException;
    List<Message> getPrivateMessages(int userId1, int userId2, int limit) throws RemoteException;
    List<Message> getGroupMessages(int groupId, int limit) throws RemoteException;
    boolean markMessagesAsRead(int receiverId, int senderId) throws RemoteException;
    int getUnreadCount(int userId) throws RemoteException;
    
    // Group operations
    Group createGroup(Group group) throws RemoteException;
    List<Group> getUserGroups(int userId) throws RemoteException;
    Group getGroupById(int groupId) throws RemoteException;
    boolean addMemberToGroup(int groupId, int userId) throws RemoteException;
    boolean removeMemberFromGroup(int groupId, int userId) throws RemoteException;
    List<User> getGroupMembers(int groupId) throws RemoteException;
    
    // Callback registration for real-time updates
    void registerClient(String clientId, ChatClientCallback callback) throws RemoteException;
    void unregisterClient(String clientId) throws RemoteException;
}
