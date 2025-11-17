package org.example.danbainoso.shared;

import org.example.danbainoso.shared.models.BlockStatus;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.shared.models.Friendship;

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
    List<Message> getPrivateMessages(int userId1, int userId2, int limit, int offset) throws RemoteException;
    List<Message> getGroupMessages(int groupId, int limit) throws RemoteException;
    List<Message> getGroupMessages(int groupId, int limit, int offset) throws RemoteException;
    boolean markMessagesAsRead(int receiverId, int senderId) throws RemoteException;
    int getUnreadCount(int userId) throws RemoteException;
    boolean editMessage(int messageId, int editorUserId, String newContent) throws RemoteException;
    boolean deleteMessage(int messageId, int requesterUserId) throws RemoteException;

    // Friendship operations
    boolean sendFriendRequest(int requesterId, int targetId) throws RemoteException;
    boolean acceptFriendRequest(int friendshipId) throws RemoteException;
    boolean rejectFriendRequest(int friendshipId) throws RemoteException;
    boolean removeFriend(int userId1, int userId2) throws RemoteException;
    boolean blockUser(int userId1, int userId2) throws RemoteException;
    boolean unblockUser(int userId1, int userId2) throws RemoteException;
    boolean isBlocked(int userId1, int userId2) throws RemoteException;
    BlockStatus getBlockStatus(int userId1, int userId2) throws RemoteException;
    List<Friendship> getFriendRequests(int userId) throws RemoteException;
    List<User> getFriends(int userId) throws RemoteException;
    
    // Group operations
    Group createGroup(Group group) throws RemoteException;
    boolean updateGroupDetails(Group group, int requesterId) throws RemoteException;
    List<Group> getUserGroups(int userId) throws RemoteException;
    Group getGroupById(int groupId) throws RemoteException;
    Group.GroupRole getGroupRole(int groupId, int userId) throws RemoteException;
    boolean addMemberToGroup(int groupId, int userId, int requesterId) throws RemoteException;
    boolean removeMemberFromGroup(int groupId, int userId, int requesterId) throws RemoteException;
    boolean deleteGroup(int groupId, int requesterId) throws RemoteException;
    List<User> getGroupMembers(int groupId) throws RemoteException;
    
    // Callback registration for real-time updates
    void registerClient(String clientId, ChatClientCallback callback) throws RemoteException;
    void unregisterClient(String clientId) throws RemoteException;
}
