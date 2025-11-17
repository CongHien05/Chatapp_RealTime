package org.example.danbainoso.client;

import org.example.danbainoso.shared.ChatClientCallback;
import org.example.danbainoso.shared.ChatService;
import org.example.danbainoso.shared.VideoClientCallback;
import org.example.danbainoso.shared.VideoService;
import org.example.danbainoso.shared.models.BlockStatus;
import org.example.danbainoso.shared.models.CallRequest;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.shared.models.Friendship;
import org.example.danbainoso.utils.Config;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.List;

public class ClientRMI {
    private static final Logger logger = LoggerUtil.getLogger(ClientRMI.class);
    
    private ChatService chatService;
    private VideoService videoService;
    private ChatClientCallback chatCallback;
    private VideoClientCallback videoCallback;
    private String clientId;
    private User currentUser;
    
    public ClientRMI() {
        this.clientId = "client_" + System.currentTimeMillis();
    }
    
    public boolean connect() {
        try {
            String host = Config.getClientRmiRegistry();
            int port = Config.getClientRmiPort();
            
            Registry registry = LocateRegistry.getRegistry(host, port);
            chatService = (ChatService) registry.lookup("ChatService");
            videoService = (VideoService) registry.lookup("VideoService");
            
            logger.info("Connected to RMI registry at {}:{}", host, port);
            return true;
        } catch (RemoteException | NotBoundException e) {
            logger.error("Failed to connect to RMI registry", e);
            return false;
        }
    }
    
    private ChatClientCallback exportedChatCallback;
    private VideoClientCallback exportedVideoCallback;
    
    public void registerCallbacks(ChatClientCallback chatCallback, VideoClientCallback videoCallback) {
        // Unregister old callbacks first to avoid "object already exported" error
        unregisterCallbacks();
        
        this.chatCallback = chatCallback;
        this.videoCallback = videoCallback;
        
        try {
            if (chatCallback != null) {
                // Only export if not already exported
                if (exportedChatCallback == null || exportedChatCallback != chatCallback) {
                    try {
                        exportedChatCallback = (ChatClientCallback) UnicastRemoteObject.exportObject(chatCallback, 0);
                    } catch (java.rmi.server.ExportException e) {
                        // Object already exported, use it directly
                        if (e.getMessage() != null && e.getMessage().contains("already exported")) {
                            exportedChatCallback = chatCallback;
                        } else {
                            throw e;
                        }
                    }
                }
                chatService.registerClient(clientId, exportedChatCallback);
            }
            
            if (videoCallback != null) {
                // Only export if not already exported
                if (exportedVideoCallback == null || exportedVideoCallback != videoCallback) {
                    try {
                        exportedVideoCallback = (VideoClientCallback) UnicastRemoteObject.exportObject(videoCallback, 0);
                    } catch (java.rmi.server.ExportException e) {
                        // Object already exported, use it directly
                        if (e.getMessage() != null && e.getMessage().contains("already exported")) {
                            exportedVideoCallback = videoCallback;
                        } else {
                            throw e;
                        }
                    }
                }
                videoService.registerCallClient(clientId, exportedVideoCallback);
            }
            
            logger.info("Callbacks registered: {}", clientId);
        } catch (RemoteException e) {
            logger.error("Failed to register callbacks", e);
        }
    }
    
    public void unregisterCallbacks() {
        try {
            if (chatService != null) {
                chatService.unregisterClient(clientId);
            }
            if (videoService != null) {
                videoService.unregisterCallClient(clientId);
            }
            logger.info("Callbacks unregistered: {}", clientId);
        } catch (RemoteException e) {
            logger.error("Failed to unregister callbacks", e);
        }
    }
    
    // Chat Service methods
    public User login(String username, String password) throws RemoteException {
        User user = chatService.login(username, password);
        if (user != null) {
            this.currentUser = user;
            clientId = "user_" + user.getUserId();
            registerCallbacks(chatCallback, videoCallback);
        }
        return user;
    }
    
    public boolean register(User user) throws RemoteException {
        return chatService.register(user);
    }
    
    public Message sendMessage(Message message) throws RemoteException {
        return chatService.sendMessage(message);
    }
    
    public java.util.List<Message> getPrivateMessages(int userId1, int userId2, int limit) throws RemoteException {
        return chatService.getPrivateMessages(userId1, userId2, limit);
    }
    
    public java.util.List<Message> getPrivateMessages(int userId1, int userId2, int limit, int offset) throws RemoteException {
        return chatService.getPrivateMessages(userId1, userId2, limit, offset);
    }
    
    public boolean markMessagesAsRead(int receiverId, int senderId) throws RemoteException {
        return chatService.markMessagesAsRead(receiverId, senderId);
    }
    
    public boolean editMessage(int messageId, String newContent) throws RemoteException {
        if (currentUser != null) {
            return chatService.editMessage(messageId, currentUser.getUserId(), newContent);
        }
        return false;
    }
    
    public boolean deleteMessage(int messageId) throws RemoteException {
        if (currentUser != null) {
            return chatService.deleteMessage(messageId, currentUser.getUserId());
        }
        return false;
    }
    
    public java.util.List<Message> getGroupMessages(int groupId, int limit) throws RemoteException {
        return chatService.getGroupMessages(groupId, limit);
    }
    
    public java.util.List<Message> getGroupMessages(int groupId, int limit, int offset) throws RemoteException {
        return chatService.getGroupMessages(groupId, limit, offset);
    }
    
    public java.util.List<org.example.danbainoso.shared.models.Group> getUserGroups(int userId) throws RemoteException {
        return chatService.getUserGroups(userId);
    }
    
    public org.example.danbainoso.shared.models.Group createGroup(org.example.danbainoso.shared.models.Group group) throws RemoteException {
        return chatService.createGroup(group);
    }
    
    public boolean updateGroupDetails(org.example.danbainoso.shared.models.Group group) throws RemoteException {
        if (currentUser != null) {
            return chatService.updateGroupDetails(group, currentUser.getUserId());
        }
        return false;
    }
    
    public org.example.danbainoso.shared.models.Group getGroupById(int groupId) throws RemoteException {
        return chatService.getGroupById(groupId);
    }
    
    public org.example.danbainoso.shared.models.Group.GroupRole getGroupRole(int groupId) throws RemoteException {
        if (currentUser != null) {
            return chatService.getGroupRole(groupId, currentUser.getUserId());
        }
        return null;
    }
    
    public java.util.List<User> getGroupMembers(int groupId) throws RemoteException {
        return chatService.getGroupMembers(groupId);
    }
    
    public boolean addMemberToGroup(int groupId, int userId) throws RemoteException {
        if (currentUser != null) {
            return chatService.addMemberToGroup(groupId, userId, currentUser.getUserId());
        }
        return false;
    }
    
    public boolean removeMemberFromGroup(int groupId, int userId) throws RemoteException {
        if (currentUser != null) {
            return chatService.removeMemberFromGroup(groupId, userId, currentUser.getUserId());
        }
        return false;
    }

    public boolean deleteGroup(int groupId) throws RemoteException {
        if (currentUser != null) {
            return chatService.deleteGroup(groupId, currentUser.getUserId());
        }
        return false;
    }
    
    public java.util.List<User> searchUsers(String keyword) throws RemoteException {
        return chatService.searchUsers(keyword);
    }

    public User getUserById(int userId) throws RemoteException {
        return chatService.getUserById(userId);
    }
    
    public boolean updateUserStatus(User.UserStatus status) throws RemoteException {
        if (currentUser != null) {
            return chatService.updateUserStatus(currentUser.getUserId(), status);
        }
        return false;
    }

    // Friendship methods
    public boolean sendFriendRequest(int targetUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.sendFriendRequest(currentUser.getUserId(), targetUserId);
        }
        return false;
    }

    public boolean acceptFriendRequest(int friendshipId) throws RemoteException {
        return chatService.acceptFriendRequest(friendshipId);
    }

    public boolean rejectFriendRequest(int friendshipId) throws RemoteException {
        return chatService.rejectFriendRequest(friendshipId);
    }

    public boolean removeFriend(int otherUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.removeFriend(currentUser.getUserId(), otherUserId);
        }
        return false;
    }

    public boolean blockUser(int otherUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.blockUser(currentUser.getUserId(), otherUserId);
        }
        return false;
    }

    public boolean unblockUser(int otherUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.unblockUser(currentUser.getUserId(), otherUserId);
        }
        return false;
    }

    public boolean isUserBlocked(int otherUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.isBlocked(currentUser.getUserId(), otherUserId);
        }
        return false;
    }

    public BlockStatus getBlockStatus(int otherUserId) throws RemoteException {
        if (currentUser != null) {
            return chatService.getBlockStatus(currentUser.getUserId(), otherUserId);
        }
        return BlockStatus.NONE;
    }

    public List<Friendship> getFriendRequests() throws RemoteException {
        if (currentUser != null) {
            return chatService.getFriendRequests(currentUser.getUserId());
        }
        return Collections.emptyList();
    }

    public List<Friendship> getFriendRequests(int userId) throws RemoteException {
        return chatService.getFriendRequests(userId);
    }

    public List<User> getFriends() throws RemoteException {
        if (currentUser != null) {
            return chatService.getFriends(currentUser.getUserId());
        }
        return Collections.emptyList();
    }

    public List<User> getFriends(int userId) throws RemoteException {
        return chatService.getFriends(userId);
    }
    
    // Video Service methods
    public CallRequest initiateCall(CallRequest callRequest) throws RemoteException {
        return videoService.initiateCall(callRequest);
    }
    
    public boolean acceptCall(String callId) throws RemoteException {
        if (currentUser != null) {
            return videoService.acceptCall(callId, currentUser.getUserId());
        }
        return false;
    }
    
    public boolean rejectCall(String callId) throws RemoteException {
        if (currentUser != null) {
            return videoService.rejectCall(callId, currentUser.getUserId());
        }
        return false;
    }
    
    public boolean endCall(String callId) throws RemoteException {
        if (currentUser != null) {
            return videoService.endCall(callId, currentUser.getUserId());
        }
        return false;
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            clientId = "user_" + user.getUserId();
        }
    }
    
    public ChatService getChatService() {
        return chatService;
    }
    
    public VideoService getVideoService() {
        return videoService;
    }
}
