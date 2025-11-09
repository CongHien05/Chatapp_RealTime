package org.example.danbainoso.server;

import org.example.danbainoso.database.GroupDAO;
import org.example.danbainoso.database.MessageDAO;
import org.example.danbainoso.database.UserDAO;
import org.example.danbainoso.shared.ChatClientCallback;
import org.example.danbainoso.shared.ChatService;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {
    private static final Logger logger = LoggerUtil.getLogger(ChatServiceImpl.class);
    
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;
    private final ConcurrentHashMap<String, ChatClientCallback> clients;
    
    public ChatServiceImpl() throws RemoteException {
        super();
        this.userDAO = new UserDAO();
        this.messageDAO = new MessageDAO();
        this.groupDAO = new GroupDAO();
        this.clients = new ConcurrentHashMap<>();
    }
    
    // User operations
    @Override
    public User login(String username, String password) throws RemoteException {
        try {
            User user = userDAO.authenticate(username, password);
            if (user != null) {
                userDAO.updateUserStatus(user.getUserId(), User.UserStatus.ONLINE);
                logger.info("User logged in: {}", username);
            }
            return user;
        } catch (SQLException e) {
            logger.error("Login failed for user: {}", username, e);
            throw new RemoteException("Login failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean register(User user) throws RemoteException {
        try {
            // Check if user already exists
            User existingUser = userDAO.getUserByUsername(user.getUsername());
            if (existingUser != null) {
                logger.warn("Registration failed: Username '{}' already exists", user.getUsername());
                return false;
            }
            
            // Check if email already exists
            existingUser = userDAO.getUserByEmail(user.getEmail());
            if (existingUser != null) {
                logger.warn("Registration failed: Email '{}' already exists", user.getEmail());
                return false;
            }
            
            userDAO.createUser(user);
            logger.info("User registered: {}", user.getUsername());
            return true;
        } catch (SQLException e) {
            logger.error("Registration failed for user: {}", user.getUsername(), e);
            // Check if it's a duplicate entry error
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                return false;
            }
            throw new RemoteException("Registration failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean updateUserStatus(int userId, User.UserStatus status) throws RemoteException {
        try {
            boolean updated = userDAO.updateUserStatus(userId, status);
            if (updated) {
                // Notify all clients about status change
                notifyUserStatusChanged(userId, status);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("Failed to update user status: {}", userId, e);
            throw new RemoteException("Failed to update status: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> searchUsers(String keyword) throws RemoteException {
        try {
            return userDAO.searchUsers(keyword);
        } catch (SQLException e) {
            logger.error("User search failed: {}", keyword, e);
            throw new RemoteException("Search failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public User getUserById(int userId) throws RemoteException {
        try {
            return userDAO.getUserById(userId);
        } catch (SQLException e) {
            logger.error("Failed to get user: {}", userId, e);
            throw new RemoteException("Failed to get user: " + e.getMessage(), e);
        }
    }
    
    // Message operations
    @Override
    public Message sendMessage(Message message) throws RemoteException {
        try {
            Message savedMessage = messageDAO.createMessage(message);
            
            // Notify recipients
            if (message.isGroupMessage()) {
                notifyGroupMessage(savedMessage);
            } else {
                notifyPrivateMessage(savedMessage);
            }
            
            logger.debug("Message sent: {}", savedMessage.getMessageId());
            return savedMessage;
        } catch (SQLException e) {
            logger.error("Failed to send message", e);
            throw new RemoteException("Failed to send message: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Message> getPrivateMessages(int userId1, int userId2, int limit) throws RemoteException {
        try {
            return messageDAO.getPrivateMessages(userId1, userId2, limit);
        } catch (SQLException e) {
            logger.error("Failed to get private messages", e);
            throw new RemoteException("Failed to get messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Message> getGroupMessages(int groupId, int limit) throws RemoteException {
        try {
            return messageDAO.getGroupMessages(groupId, limit);
        } catch (SQLException e) {
            logger.error("Failed to get group messages", e);
            throw new RemoteException("Failed to get messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean markMessagesAsRead(int receiverId, int senderId) throws RemoteException {
        try {
            return messageDAO.markMessagesAsRead(receiverId, senderId) > 0;
        } catch (SQLException e) {
            logger.error("Failed to mark messages as read", e);
            throw new RemoteException("Failed to mark messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int getUnreadCount(int userId) throws RemoteException {
        try {
            return messageDAO.getUnreadCount(userId);
        } catch (SQLException e) {
            logger.error("Failed to get unread count", e);
            throw new RemoteException("Failed to get unread count: " + e.getMessage(), e);
        }
    }
    
    // Group operations
    @Override
    public Group createGroup(Group group) throws RemoteException {
        try {
            Group createdGroup = groupDAO.createGroup(group);
            logger.info("Group created: {}", createdGroup.getGroupName());
            return createdGroup;
        } catch (SQLException e) {
            logger.error("Failed to create group", e);
            throw new RemoteException("Failed to create group: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Group> getUserGroups(int userId) throws RemoteException {
        try {
            return groupDAO.getUserGroups(userId);
        } catch (SQLException e) {
            logger.error("Failed to get user groups", e);
            throw new RemoteException("Failed to get groups: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Group getGroupById(int groupId) throws RemoteException {
        try {
            return groupDAO.getGroupById(groupId);
        } catch (SQLException e) {
            logger.error("Failed to get group: {}", groupId, e);
            throw new RemoteException("Failed to get group: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean addMemberToGroup(int groupId, int userId) throws RemoteException {
        try {
            boolean added = groupDAO.addMember(groupId, userId, "MEMBER");
            if (added) {
                try {
                    User user = userDAO.getUserById(userId);
                    notifyUserJoinedGroup(groupId, user);
                } catch (SQLException e) {
                    logger.error("Failed to get user for notification", e);
                }
            }
            return added;
        } catch (SQLException e) {
            logger.error("Failed to add member to group", e);
            throw new RemoteException("Failed to add member: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean removeMemberFromGroup(int groupId, int userId) throws RemoteException {
        try {
            boolean removed = groupDAO.removeMember(groupId, userId);
            if (removed) {
                notifyUserLeftGroup(groupId, userId);
            }
            return removed;
        } catch (SQLException e) {
            logger.error("Failed to remove member from group", e);
            throw new RemoteException("Failed to remove member: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> getGroupMembers(int groupId) throws RemoteException {
        try {
            return groupDAO.getGroupMembers(groupId);
        } catch (SQLException e) {
            logger.error("Failed to get group members", e);
            throw new RemoteException("Failed to get members: " + e.getMessage(), e);
        }
    }
    
    // Callback registration
    @Override
    public void registerClient(String clientId, ChatClientCallback callback) throws RemoteException {
        clients.put(clientId, callback);
        logger.info("Client registered: {}", clientId);
    }
    
    @Override
    public void unregisterClient(String clientId) throws RemoteException {
        clients.remove(clientId);
        logger.info("Client unregistered: {}", clientId);
    }
    
    // Notification methods
    private void notifyPrivateMessage(Message message) {
        String receiverKey = "user_" + message.getReceiverId();
        String senderKey = "user_" + message.getSenderId();
        
        // Notify receiver
        ChatClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onMessageReceived(message);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver", e);
                clients.remove(receiverKey);
            }
        }
        
        // Notify sender (for confirmation)
        ChatClientCallback sender = clients.get(senderKey);
        if (sender != null) {
            try {
                sender.onMessageReceived(message);
            } catch (RemoteException e) {
                logger.error("Failed to notify sender", e);
                clients.remove(senderKey);
            }
        }
    }
    
    private void notifyGroupMessage(Message message) {
        try {
            List<User> members = groupDAO.getGroupMembers(message.getGroupId());
            for (User member : members) {
                if (member.getUserId() != message.getSenderId()) {
                    String memberKey = "user_" + member.getUserId();
                    ChatClientCallback callback = clients.get(memberKey);
                    if (callback != null) {
                        try {
                            callback.onMessageReceived(message);
                        } catch (RemoteException e) {
                            logger.error("Failed to notify group member", e);
                            clients.remove(memberKey);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get group members for notification", e);
        }
    }
    
    private void notifyUserStatusChanged(int userId, User.UserStatus status) {
        for (ChatClientCallback callback : clients.values()) {
            try {
                callback.onUserStatusChanged(userId, status);
            } catch (RemoteException e) {
                logger.error("Failed to notify status change", e);
            }
        }
    }
    
    private void notifyUserJoinedGroup(int groupId, User user) {
        try {
            List<User> members = groupDAO.getGroupMembers(groupId);
            for (User member : members) {
                if (member.getUserId() != user.getUserId()) {
                    String memberKey = "user_" + member.getUserId();
                    ChatClientCallback callback = clients.get(memberKey);
                    if (callback != null) {
                        try {
                            callback.onUserJoinedGroup(groupId, user);
                        } catch (RemoteException e) {
                            logger.error("Failed to notify user joined group", e);
                            clients.remove(memberKey);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to notify user joined group", e);
        }
    }
    
    private void notifyUserLeftGroup(int groupId, int userId) {
        try {
            List<User> members = groupDAO.getGroupMembers(groupId);
            for (User member : members) {
                String memberKey = "user_" + member.getUserId();
                ChatClientCallback callback = clients.get(memberKey);
                if (callback != null) {
                    try {
                        callback.onUserLeftGroup(groupId, userId);
                    } catch (RemoteException e) {
                        logger.error("Failed to notify user left group", e);
                        clients.remove(memberKey);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to notify user left group", e);
        }
    }
}
