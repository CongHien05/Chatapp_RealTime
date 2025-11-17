package org.example.danbainoso.server;

import org.example.danbainoso.database.FriendshipDAO;
import org.example.danbainoso.database.GroupDAO;
import org.example.danbainoso.database.MessageDAO;
import org.example.danbainoso.database.UserDAO;
import org.example.danbainoso.shared.ChatClientCallback;
import org.example.danbainoso.shared.ChatService;
import org.example.danbainoso.shared.models.BlockStatus;
import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.shared.models.Friendship;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {
    private static final Logger logger = LoggerUtil.getLogger(ChatServiceImpl.class);
    
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;
    private final FriendshipDAO friendshipDAO;
    private final ConcurrentHashMap<String, ChatClientCallback> clients;
    
    public ChatServiceImpl() throws RemoteException {
        super();
        this.userDAO = new UserDAO();
        this.messageDAO = new MessageDAO();
        this.groupDAO = new GroupDAO();
        this.friendshipDAO = new FriendshipDAO();
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
            if (message.getReceiverId() != null) {
                BlockStatus status = friendshipDAO.getBlockStatus(message.getSenderId(), message.getReceiverId());
                if (status == BlockStatus.BLOCKED_BY_OTHER) {
                    logger.warn("Sender {} blocked by receiver {}", message.getSenderId(), message.getReceiverId());
                    throw new RemoteException("Bạn đã bị chặn bởi người này.");
                } else if (status == BlockStatus.BLOCKED_BY_ME) {
                    logger.warn("Sender {} blocks receiver {}", message.getSenderId(), message.getReceiverId());
                    throw new RemoteException("Bạn đã chặn người này. Bỏ chặn để tiếp tục trò chuyện.");
                }
            }

            Message savedMessage = messageDAO.createMessage(message);
            enrichSenderMetadata(savedMessage);
            
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
        return getPrivateMessages(userId1, userId2, limit, 0);
    }
    
    @Override
    public List<Message> getPrivateMessages(int userId1, int userId2, int limit, int offset) throws RemoteException {
        try {
            if (friendshipDAO.getBlockStatus(userId1, userId2) != BlockStatus.NONE) {
                logger.debug("Hide messages between {} and {} due to block", userId1, userId2);
                return Collections.emptyList();
            }
            return messageDAO.getPrivateMessages(userId1, userId2, limit, offset);
        } catch (SQLException e) {
            logger.error("Failed to get private messages", e);
            throw new RemoteException("Failed to get messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Message> getGroupMessages(int groupId, int limit) throws RemoteException {
        return getGroupMessages(groupId, limit, 0);
    }
    
    @Override
    public List<Message> getGroupMessages(int groupId, int limit, int offset) throws RemoteException {
        try {
            return messageDAO.getGroupMessages(groupId, limit, offset);
        } catch (SQLException e) {
            logger.error("Failed to get group messages", e);
            throw new RemoteException("Failed to get messages: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean markMessagesAsRead(int receiverId, int senderId) throws RemoteException {
        try {
            boolean updated = messageDAO.markMessagesAsRead(receiverId, senderId) > 0;
            if (updated) {
                notifyMessagesRead(receiverId, senderId);
            }
            return updated;
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
    
    @Override
    public boolean editMessage(int messageId, int editorUserId, String newContent) throws RemoteException {
        try {
            Message msg = messageDAO.getMessageById(messageId);
            if (msg == null || msg.getSenderId() != editorUserId) {
                return false;
            }
            boolean updated = messageDAO.updateMessage(messageId, newContent);
            if (updated) {
                Message updatedMsg = messageDAO.getMessageById(messageId);
                notifyMessageUpdated(updatedMsg);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("Failed to edit message", e);
            throw new RemoteException("Failed to edit message: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean deleteMessage(int messageId, int requesterUserId) throws RemoteException {
        try {
            Message msg = messageDAO.getMessageById(messageId);
            if (msg == null || msg.getSenderId() != requesterUserId) {
                return false;
            }
            boolean deleted = messageDAO.softDeleteMessage(messageId);
            if (deleted) {
                notifyMessageDeleted(messageId, msg);
            }
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to delete message", e);
            throw new RemoteException("Failed to delete message: " + e.getMessage(), e);
        }
    }
    
    // Friendship operations
    @Override
    public boolean sendFriendRequest(int requesterId, int targetId) throws RemoteException {
        if (requesterId == targetId) {
            return false;
        }
        try {
            Friendship friendship = friendshipDAO.sendFriendRequest(requesterId, targetId);
            if (friendship != null) {
                notifyFriendRequestReceived(friendship);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to send friend request from {} to {}", requesterId, targetId, e);
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                return false;
            }
            throw new RemoteException("Failed to send friend request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean acceptFriendRequest(int friendshipId) throws RemoteException {
        try {
            return friendshipDAO.acceptFriendRequest(friendshipId);
        } catch (SQLException e) {
            logger.error("Failed to accept friend request {}", friendshipId, e);
            throw new RemoteException("Failed to accept friend request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean rejectFriendRequest(int friendshipId) throws RemoteException {
        try {
            return friendshipDAO.rejectFriendRequest(friendshipId);
        } catch (SQLException e) {
            logger.error("Failed to reject friend request {}", friendshipId, e);
            throw new RemoteException("Failed to reject friend request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean removeFriend(int userId1, int userId2) throws RemoteException {
        try {
            return friendshipDAO.removeFriend(userId1, userId2);
        } catch (SQLException e) {
            logger.error("Failed to remove friendship between {} and {}", userId1, userId2, e);
            throw new RemoteException("Failed to remove friend: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean blockUser(int userId1, int userId2) throws RemoteException {
        try {
            return friendshipDAO.blockUser(userId1, userId2);
        } catch (SQLException e) {
            logger.error("Failed to block between {} and {}", userId1, userId2, e);
            throw new RemoteException("Failed to block user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean unblockUser(int userId1, int userId2) throws RemoteException {
        try {
            return friendshipDAO.unblockUser(userId1, userId2);
        } catch (SQLException e) {
            logger.error("Failed to unblock between {} and {}", userId1, userId2, e);
            throw new RemoteException("Failed to unblock user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBlocked(int userId1, int userId2) throws RemoteException {
        try {
            return friendshipDAO.isBlocked(userId1, userId2);
        } catch (SQLException e) {
            logger.error("Failed to verify block status between {} and {}", userId1, userId2, e);
            throw new RemoteException("Failed to check block status: " + e.getMessage(), e);
        }
    }

    @Override
    public BlockStatus getBlockStatus(int userId1, int userId2) throws RemoteException {
        try {
            return friendshipDAO.getBlockStatus(userId1, userId2);
        } catch (SQLException e) {
            logger.error("Failed to fetch block status between {} and {}", userId1, userId2, e);
            throw new RemoteException("Failed to get block status: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Friendship> getFriendRequests(int userId) throws RemoteException {
        try {
            return friendshipDAO.getFriendRequests(userId);
        } catch (SQLException e) {
            logger.error("Failed to get friend requests for {}", userId, e);
            throw new RemoteException("Failed to get friend requests: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> getFriends(int userId) throws RemoteException {
        try {
            List<Friendship> friendships = friendshipDAO.getFriends(userId);
            List<User> friends = new ArrayList<>();
            for (Friendship friendship : friendships) {
                int friendId = friendship.getUser1Id() == userId ? friendship.getUser2Id() : friendship.getUser1Id();
                try {
                    User friend = userDAO.getUserById(friendId);
                    if (friend != null) {
                        friends.add(friend);
                    }
                } catch (SQLException e) {
                    logger.error("Failed to load friend user {}", friendId, e);
                }
            }
            return friends;
        } catch (SQLException e) {
            logger.error("Failed to get friends for {}", userId, e);
            throw new RemoteException("Failed to get friends: " + e.getMessage(), e);
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
    public boolean updateGroupDetails(Group group, int requesterId) throws RemoteException {
        if (group == null) {
            return false;
        }
        try {
            Group.GroupRole role = groupDAO.checkUserRole(group.getGroupId(), requesterId);
            if (role != Group.GroupRole.ADMIN) {
                logger.warn("User {} attempted to update group {} without admin rights", requesterId, group.getGroupId());
                return false;
            }
            Group existing = groupDAO.getGroupById(group.getGroupId());
            if (existing == null) {
                return false;
            }
            existing.setGroupName(group.getGroupName());
            existing.setDescription(group.getDescription());
            existing.setAvatarUrl(group.getAvatarUrl());
            boolean updated = groupDAO.updateGroup(existing);
            if (updated) {
                logger.info("Group {} updated by user {}", group.getGroupId(), requesterId);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("Failed to update group {}", group != null ? group.getGroupId() : "unknown", e);
            throw new RemoteException("Failed to update group: " + e.getMessage(), e);
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
    public Group.GroupRole getGroupRole(int groupId, int userId) throws RemoteException {
        try {
            return groupDAO.checkUserRole(groupId, userId);
        } catch (SQLException e) {
            logger.error("Failed to get role for user {} in group {}", userId, groupId, e);
            throw new RemoteException("Failed to get group role: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean addMemberToGroup(int groupId, int userId, int requesterId) throws RemoteException {
        try {
            Group.GroupRole requesterRole = groupDAO.checkUserRole(groupId, requesterId);
            if (requesterRole != Group.GroupRole.ADMIN) {
                logger.warn("User {} attempted to add member {} to group {} without admin rights", requesterId, userId, groupId);
                return false;
            }
            boolean added = groupDAO.addMember(groupId, userId, Group.GroupRole.MEMBER.name());
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
    public boolean removeMemberFromGroup(int groupId, int userId, int requesterId) throws RemoteException {
        try {
            if (requesterId != userId) {
                Group.GroupRole requesterRole = groupDAO.checkUserRole(groupId, requesterId);
                if (requesterRole != Group.GroupRole.ADMIN) {
                    logger.warn("User {} attempted to remove member {} from group {} without admin rights", requesterId, userId, groupId);
                    return false;
                }
            }
            boolean removed = groupDAO.removeMember(groupId, userId);
            if (removed) {
                if (requesterId == userId) {
                    logger.info("User {} left group {}", userId, groupId);
                } else {
                    logger.info("User {} removed user {} from group {}", requesterId, userId, groupId);
                }
                notifyUserLeftGroup(groupId, userId);
            }
            return removed;
        } catch (SQLException e) {
            logger.error("Failed to remove member from group", e);
            throw new RemoteException("Failed to remove member: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteGroup(int groupId, int requesterId) throws RemoteException {
        try {
            Group.GroupRole requesterRole = groupDAO.checkUserRole(groupId, requesterId);
            if (requesterRole != Group.GroupRole.ADMIN) {
                logger.warn("User {} attempted to delete group {} without admin rights", requesterId, groupId);
                return false;
            }
            int memberCount = groupDAO.getMemberCount(groupId);
            if (memberCount > 1) {
                logger.warn("Group {} delete blocked because {} members remain", groupId, memberCount);
                return false;
            }
            boolean deleted = groupDAO.deleteGroup(groupId);
            if (deleted) {
                logger.info("Group {} deleted by {}", groupId, requesterId);
            }
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to delete group {}", groupId, e);
            throw new RemoteException("Failed to delete group: " + e.getMessage(), e);
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
            // Also notify the removed user themselves so their UI can update
            String removedKey = "user_" + userId;
            ChatClientCallback removedCallback = clients.get(removedKey);
            if (removedCallback != null) {
                try {
                    removedCallback.onUserLeftGroup(groupId, userId);
                } catch (RemoteException e) {
                    logger.error("Failed to notify removed user {} left group {}", userId, groupId, e);
                    clients.remove(removedKey);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to notify user left group", e);
        }
    }
    
    private void notifyMessagesRead(int readerId, int senderId) {
        String senderKey = "user_" + senderId;
        ChatClientCallback senderCallback = clients.get(senderKey);
        if (senderCallback != null) {
            try {
                senderCallback.onMessagesMarkedAsRead(readerId, senderId);
            } catch (RemoteException e) {
                logger.error("Failed to notify messages read", e);
                clients.remove(senderKey);
            }
        }
    }
    
    private void notifyMessageUpdated(Message message) {
        if (message.isGroupMessage()) {
            notifyGroupMessageUpdate(message);
        } else {
            notifyPrivateMessageUpdate(message);
        }
    }
    
    private void notifyPrivateMessageUpdate(Message message) {
        String receiverKey = "user_" + message.getReceiverId();
        String senderKey = "user_" + message.getSenderId();
        
        ChatClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onMessageUpdated(message);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver updated", e);
                clients.remove(receiverKey);
            }
        }
        
        ChatClientCallback sender = clients.get(senderKey);
        if (sender != null) {
            try {
                sender.onMessageUpdated(message);
            } catch (RemoteException e) {
                logger.error("Failed to notify sender updated", e);
                clients.remove(senderKey);
            }
        }
    }
    
    private void notifyGroupMessageUpdate(Message message) {
        try {
            List<User> members = groupDAO.getGroupMembers(message.getGroupId());
            for (User member : members) {
                String memberKey = "user_" + member.getUserId();
                ChatClientCallback callback = clients.get(memberKey);
                if (callback != null) {
                    try {
                        callback.onMessageUpdated(message);
                    } catch (RemoteException e) {
                        logger.error("Failed to notify group updated", e);
                        clients.remove(memberKey);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to notify group message update", e);
        }
    }
    
    private void notifyMessageDeleted(int messageId, Message original) {
        if (original.isGroupMessage()) {
            notifyGroupMessageDeleted(messageId, original.getGroupId());
        } else {
            notifyPrivateMessageDeleted(messageId, original.getReceiverId(), original.getSenderId());
        }
    }
    
    private void notifyPrivateMessageDeleted(int messageId, int receiverId, int senderId) {
        String receiverKey = "user_" + receiverId;
        String senderKey = "user_" + senderId;
        
        ChatClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onMessageDeleted(messageId);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver deleted", e);
                clients.remove(receiverKey);
            }
        }
        
        ChatClientCallback sender = clients.get(senderKey);
        if (sender != null) {
            try {
                sender.onMessageDeleted(messageId);
            } catch (RemoteException e) {
                logger.error("Failed to notify sender deleted", e);
                clients.remove(senderKey);
            }
        }
    }
    
    private void notifyGroupMessageDeleted(int messageId, int groupId) {
        try {
            List<User> members = groupDAO.getGroupMembers(groupId);
            for (User member : members) {
                String memberKey = "user_" + member.getUserId();
                ChatClientCallback callback = clients.get(memberKey);
                if (callback != null) {
                    try {
                        callback.onMessageDeleted(messageId);
                    } catch (RemoteException e) {
                        logger.error("Failed to notify group deleted", e);
                        clients.remove(memberKey);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to notify group message deleted", e);
        }
    }
    
    private void notifyFriendRequestReceived(Friendship friendship) {
        String receiverKey = "user_" + friendship.getUser2Id();
        ChatClientCallback callback = clients.get(receiverKey);
        if (callback != null) {
            try {
                callback.onFriendRequestReceived(friendship);
            } catch (RemoteException e) {
                logger.error("Failed to notify friend request received", e);
                clients.remove(receiverKey);
            }
        }
    }

    private void enrichSenderMetadata(Message message) {
        if (message == null) {
            return;
        }
        try {
            User sender = userDAO.getUserById(message.getSenderId());
            if (sender != null) {
                message.setSenderName(sender.getUsername());
                message.setSenderAvatar(sender.getAvatarUrl());
            }
        } catch (SQLException e) {
            logger.warn("Failed to enrich sender metadata for message {}", message.getMessageId(), e);
        }
    }
}
