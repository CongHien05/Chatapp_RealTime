package org.example.danbainoso.client;

import org.example.danbainoso.shared.ChatClientCallback;
import org.example.danbainoso.shared.ChatService;
import org.example.danbainoso.shared.VideoClientCallback;
import org.example.danbainoso.shared.VideoService;
import org.example.danbainoso.shared.models.CallRequest;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.Config;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

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
    
    public void registerCallbacks(ChatClientCallback chatCallback, VideoClientCallback videoCallback) {
        this.chatCallback = chatCallback;
        this.videoCallback = videoCallback;
        
        try {
            if (chatCallback != null) {
                ChatClientCallback stub = (ChatClientCallback) UnicastRemoteObject.exportObject(chatCallback, 0);
                chatService.registerClient(clientId, stub);
            }
            
            if (videoCallback != null) {
                VideoClientCallback stub = (VideoClientCallback) UnicastRemoteObject.exportObject(videoCallback, 0);
                videoService.registerCallClient(clientId, stub);
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
    
    public java.util.List<Message> getGroupMessages(int groupId, int limit) throws RemoteException {
        return chatService.getGroupMessages(groupId, limit);
    }
    
    public java.util.List<org.example.danbainoso.shared.models.Group> getUserGroups(int userId) throws RemoteException {
        return chatService.getUserGroups(userId);
    }
    
    public org.example.danbainoso.shared.models.Group createGroup(org.example.danbainoso.shared.models.Group group) throws RemoteException {
        return chatService.createGroup(group);
    }
    
    public java.util.List<User> searchUsers(String keyword) throws RemoteException {
        return chatService.searchUsers(keyword);
    }
    
    public boolean updateUserStatus(User.UserStatus status) throws RemoteException {
        if (currentUser != null) {
            return chatService.updateUserStatus(currentUser.getUserId(), status);
        }
        return false;
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
