package org.example.danbainoso.database;

import org.example.danbainoso.shared.models.Message;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    private static final Logger logger = LoggerUtil.getLogger(MessageDAO.class);
    
    // Create message
    public Message createMessage(Message message) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, group_id, content, message_type, file_url, is_read, is_edited, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, message.getSenderId());
            if (message.getReceiverId() != null) {
                pstmt.setInt(2, message.getReceiverId());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }
            if (message.getGroupId() != null) {
                pstmt.setInt(3, message.getGroupId());
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }
            pstmt.setString(4, message.getContent());
            pstmt.setString(5, message.getMessageType().name());
            pstmt.setString(6, message.getFileUrl());
            pstmt.setBoolean(7, message.isRead());
            pstmt.setBoolean(8, message.isEdited());
            pstmt.setBoolean(9, message.isDeleted());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating message failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setMessageId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating message failed, no ID obtained.");
                }
            }
            
            // Get created timestamp
            message.setCreatedAt(getMessageById(message.getMessageId()).getCreatedAt());
            
            logger.debug("Message created: {}", message.getMessageId());
            return message;
        }
    }
    
    // Get message by ID
    public Message getMessageById(int messageId) throws SQLException {
        String sql = "SELECT m.*, u.username as sender_name, u.avatar_url as sender_avatar " +
                     "FROM messages m " +
                     "LEFT JOIN users u ON m.sender_id = u.user_id " +
                     "WHERE m.message_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, messageId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMessage(rs);
                }
            }
        }
        return null;
    }
    
    // Get private messages between two users
    public List<Message> getPrivateMessages(int userId1, int userId2, int limit) throws SQLException {
        return getPrivateMessages(userId1, userId2, limit, 0);
    }
    
    public List<Message> getPrivateMessages(int userId1, int userId2, int limit, int offset) throws SQLException {
        String sql = "SELECT m.*, u.username as sender_name, u.avatar_url as sender_avatar " +
                     "FROM messages m " +
                     "LEFT JOIN users u ON m.sender_id = u.user_id " +
                     "WHERE ((m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?)) " +
                     "AND m.group_id IS NULL " +
                     "ORDER BY m.created_at DESC LIMIT ? OFFSET ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);
            pstmt.setInt(5, limit);
            pstmt.setInt(6, offset);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        }
        
        // Reverse to get chronological order
        List<Message> reversed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversed.add(messages.get(i));
        }
        return reversed;
    }
    
    // Get group messages
    public List<Message> getGroupMessages(int groupId, int limit) throws SQLException {
        return getGroupMessages(groupId, limit, 0);
    }
    
    public List<Message> getGroupMessages(int groupId, int limit, int offset) throws SQLException {
        String sql = "SELECT m.*, u.username as sender_name, u.avatar_url as sender_avatar " +
                     "FROM messages m " +
                     "LEFT JOIN users u ON m.sender_id = u.user_id " +
                     "WHERE m.group_id = ? " +
                     "ORDER BY m.created_at DESC LIMIT ? OFFSET ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, limit);
            pstmt.setInt(3, offset);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        }
        
        // Reverse to get chronological order
        List<Message> reversed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversed.add(messages.get(i));
        }
        return reversed;
    }
    
    // Mark messages as read
    public int markMessagesAsRead(int receiverId, int senderId) throws SQLException {
        String sql = "UPDATE messages SET is_read = TRUE WHERE receiver_id = ? AND sender_id = ? AND is_read = FALSE";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, receiverId);
            pstmt.setInt(2, senderId);
            
            return pstmt.executeUpdate();
        }
    }
    
    // Mark group messages as read
    public int markGroupMessagesAsRead(int userId, int groupId) throws SQLException {
        String sql = "UPDATE messages SET is_read = TRUE WHERE group_id = ? AND sender_id != ? AND is_read = FALSE";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            
            return pstmt.executeUpdate();
        }
    }
    
    // Get unread message count for user
    public int getUnreadCount(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM messages WHERE receiver_id = ? AND is_read = FALSE";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }
    
    // Update message content (edit)
    public boolean updateMessage(int messageId, String newContent) throws SQLException {
        String sql = "UPDATE messages SET content = ?, is_edited = TRUE WHERE message_id = ? AND is_deleted = FALSE";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newContent);
            pstmt.setInt(2, messageId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    // Soft delete message
    public boolean softDeleteMessage(int messageId) throws SQLException {
        String sql = "UPDATE messages SET is_deleted = TRUE WHERE message_id = ? AND is_deleted = FALSE";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, messageId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    // Map ResultSet to Message object
    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setMessageId(rs.getInt("message_id"));
        message.setSenderId(rs.getInt("sender_id"));
        
        int receiverId = rs.getInt("receiver_id");
        if (!rs.wasNull()) {
            message.setReceiverId(receiverId);
        }
        
        int groupId = rs.getInt("group_id");
        if (!rs.wasNull()) {
            message.setGroupId(groupId);
        }
        
        message.setContent(rs.getString("content"));
        message.setMessageType(Message.MessageType.valueOf(rs.getString("message_type")));
        message.setFileUrl(rs.getString("file_url"));
        message.setRead(rs.getBoolean("is_read"));
        try { message.setEdited(rs.getBoolean("is_edited")); } catch (SQLException ignored) {}
        try { message.setDeleted(rs.getBoolean("is_deleted")); } catch (SQLException ignored) {}
        message.setCreatedAt(rs.getTimestamp("created_at"));
        
        // Set sender info if available
        try {
            message.setSenderName(rs.getString("sender_name"));
            message.setSenderAvatar(rs.getString("sender_avatar"));
        } catch (SQLException e) {
            // Column might not exist in some queries
        }
        
        return message;
    }
}
