package org.example.danbainoso.database;

import org.example.danbainoso.shared.models.Group;
import org.example.danbainoso.shared.models.User;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    private static final Logger logger = LoggerUtil.getLogger(GroupDAO.class);
    private final UserDAO userDAO = new UserDAO();
    
    // Create group
    public Group createGroup(Group group) throws SQLException {
        String sql = "INSERT INTO groups (group_name, description, avatar_url, created_by) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, group.getGroupName());
            pstmt.setString(2, group.getDescription());
            pstmt.setString(3, group.getAvatarUrl());
            pstmt.setInt(4, group.getCreatedBy());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating group failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    group.setGroupId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating group failed, no ID obtained.");
                }
            }
            
            // Add creator as admin member
            addMember(group.getGroupId(), group.getCreatedBy(), "ADMIN");
            
            logger.info("Group created: {} by user {}", group.getGroupName(), group.getCreatedBy());
            return group;
        }
    }
    
    // Get group by ID
    public Group getGroupById(int groupId) throws SQLException {
        String sql = "SELECT g.*, u.username as creator_name " +
                     "FROM groups g " +
                     "LEFT JOIN users u ON g.created_by = u.user_id " +
                     "WHERE g.group_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Group group = mapResultSetToGroup(rs);
                    group.setMembers(getGroupMembers(groupId));
                    return group;
                }
            }
        }
        return null;
    }
    
    // Get all groups for a user
    public List<Group> getUserGroups(int userId) throws SQLException {
        String sql = "SELECT DISTINCT g.*, u.username as creator_name " +
                     "FROM groups g " +
                     "LEFT JOIN users u ON g.created_by = u.user_id " +
                     "INNER JOIN group_members gm ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ? " +
                     "ORDER BY g.updated_at DESC";
        
        List<Group> groups = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Group group = mapResultSetToGroup(rs);
                    group.setMemberCount(getMemberCount(group.getGroupId()));
                    groups.add(group);
                }
            }
        }
        return groups;
    }
    
    // Update group
    public boolean updateGroup(Group group) throws SQLException {
        String sql = "UPDATE groups SET group_name = ?, description = ?, avatar_url = ? WHERE group_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, group.getGroupName());
            pstmt.setString(2, group.getDescription());
            pstmt.setString(3, group.getAvatarUrl());
            pstmt.setInt(4, group.getGroupId());
            
            int affectedRows = pstmt.executeUpdate();
            logger.info("Group updated: {} ({} rows affected)", group.getGroupName(), affectedRows);
            return affectedRows > 0;
        }
    }
    
    // Delete group
    public boolean deleteGroup(int groupId) throws SQLException {
        String sql = "DELETE FROM groups WHERE group_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            int affectedRows = pstmt.executeUpdate();
            logger.info("Group deleted: {} ({} rows affected)", groupId, affectedRows);
            return affectedRows > 0;
        }
    }
    
    // Add member to group
    public boolean addMember(int groupId, int userId, String role) throws SQLException {
        String sql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE role = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            pstmt.setString(3, role);
            pstmt.setString(4, role);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    // Remove member from group
    public boolean removeMember(int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    // Get group members
    public List<User> getGroupMembers(int groupId) throws SQLException {
        String sql = "SELECT u.* FROM users u " +
                     "INNER JOIN group_members gm ON u.user_id = gm.user_id " +
                     "WHERE gm.group_id = ? " +
                     "ORDER BY gm.role DESC, u.username";
        
        List<User> members = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(userDAO.mapResultSetToUser(rs));
                }
            }
        }
        return members;
    }
    
    // Get member count
    public int getMemberCount(int groupId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM group_members WHERE group_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }
    
    // Check if user is member
    public boolean isMember(int groupId, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM group_members WHERE group_id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }
    
    // Get user role in group
    public String getUserRole(int groupId, int userId) throws SQLException {
        String sql = "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        }
        return null;
    }
    
    // Map ResultSet to Group object
    private Group mapResultSetToGroup(ResultSet rs) throws SQLException {
        Group group = new Group();
        group.setGroupId(rs.getInt("group_id"));
        group.setGroupName(rs.getString("group_name"));
        group.setDescription(rs.getString("description"));
        group.setAvatarUrl(rs.getString("avatar_url"));
        group.setCreatedBy(rs.getInt("created_by"));
        group.setCreatedAt(rs.getTimestamp("created_at"));
        group.setUpdatedAt(rs.getTimestamp("updated_at"));
        
        try {
            group.setCreatorName(rs.getString("creator_name"));
        } catch (SQLException e) {
            // Column might not exist in some queries
        }
        
        return group;
    }
}
