package org.example.danbainoso.database;

import org.example.danbainoso.shared.models.BlockStatus;
import org.example.danbainoso.shared.models.Friendship;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendshipDAO {
    private static final Logger logger = LoggerUtil.getLogger(FriendshipDAO.class);

    public Friendship sendFriendRequest(int userId1, int userId2) throws SQLException {
        String sql = "INSERT INTO friendships (user1_id, user2_id, status) VALUES (?, ?, 'PENDING')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Sending friend request failed, no rows affected.");
            }

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    logger.debug("Friend request created between {} and {}", userId1, userId2);
                    return getFriendshipById(rs.getInt(1));
                }
            }
        }

        return null;
    }

    public boolean acceptFriendRequest(int friendshipId) throws SQLException {
        String sql = "UPDATE friendships SET status = 'ACCEPTED', updated_at = CURRENT_TIMESTAMP WHERE friendship_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, friendshipId);
            boolean updated = pstmt.executeUpdate() > 0;
            if (updated) {
                logger.debug("Friend request {} accepted", friendshipId);
            }
            return updated;
        }
    }

    public boolean rejectFriendRequest(int friendshipId) throws SQLException {
        String sql = "DELETE FROM friendships WHERE friendship_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, friendshipId);
            boolean deleted = pstmt.executeUpdate() > 0;
            if (deleted) {
                logger.debug("Friend request {} rejected", friendshipId);
            }
            return deleted;
        }
    }

    public List<Friendship> getFriendRequests(int userId) throws SQLException {
        String sql = "SELECT * FROM friendships WHERE user2_id = ? AND status = 'PENDING'";
        List<Friendship> requests = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapResultSet(rs));
                }
            }
        }

        return requests;
    }

    public List<Friendship> getFriends(int userId) throws SQLException {
        String sql = "SELECT * FROM friendships " +
                     "WHERE status = 'ACCEPTED' AND (user1_id = ? OR user2_id = ?)";
        List<Friendship> friends = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    friends.add(mapResultSet(rs));
                }
            }
        }

        return friends;
    }

    public boolean removeFriend(int userId1, int userId2) throws SQLException {
        String sql = "DELETE FROM friendships WHERE status = 'ACCEPTED' AND " +
                     "((user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?))";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);

            boolean removed = pstmt.executeUpdate() > 0;
            if (removed) {
                logger.debug("Friendship removed between {} and {}", userId1, userId2);
            }
            return removed;
        }
    }

    public boolean blockUser(int userId1, int userId2) throws SQLException {
        // Try to update existing relationship in either direction and reassign ownership
        String updateSql = "UPDATE friendships SET user1_id = ?, user2_id = ?, status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP " +
                "WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId1);
            pstmt.setInt(4, userId2);
            pstmt.setInt(5, userId2);
            pstmt.setInt(6, userId1);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                logger.debug("Friendship between {} and {} blocked", userId1, userId2);
                return true;
            }
        }

        // Create new record if no relationship exists
        String insertSql = "INSERT INTO friendships (user1_id, user2_id, status) VALUES (?, ?, 'BLOCKED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            boolean inserted = pstmt.executeUpdate() > 0;
            if (inserted) {
                logger.debug("Friendship between {} and {} blocked (new record)", userId1, userId2);
            }
            return inserted;
        }
    }

    public boolean unblockUser(int userId1, int userId2) throws SQLException {
        String sql = "DELETE FROM friendships WHERE status = 'BLOCKED' AND " +
                "((user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?))";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);

            boolean deleted = pstmt.executeUpdate() > 0;
            if (deleted) {
                logger.debug("Friendship block removed between {} and {}", userId1, userId2);
            }
            return deleted;
        }
    }

    public boolean isBlocked(int userId1, int userId2) throws SQLException {
        String sql = "SELECT 1 FROM friendships WHERE status = 'BLOCKED' AND " +
                "((user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)) LIMIT 1";

        boolean blocked = false;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);

            try (ResultSet rs = pstmt.executeQuery()) {
                blocked = rs.next();
            }
        }
        return blocked;
    }

    public BlockStatus getBlockStatus(int requesterId, int targetId) throws SQLException {
        String sql = "SELECT user1_id FROM friendships WHERE status = 'BLOCKED' AND " +
                "((user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?))";

        BlockStatus status = BlockStatus.NONE;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, requesterId);
            pstmt.setInt(2, targetId);
            pstmt.setInt(3, targetId);
            pstmt.setInt(4, requesterId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int blockerId = rs.getInt("user1_id");
                    status = (blockerId == requesterId)
                            ? BlockStatus.BLOCKED_BY_ME
                            : BlockStatus.BLOCKED_BY_OTHER;
                }
            }
        }
        return status;
    }

    public Friendship getFriendshipById(int friendshipId) throws SQLException {
        String sql = "SELECT * FROM friendships WHERE friendship_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, friendshipId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    private Friendship mapResultSet(ResultSet rs) throws SQLException {
        Friendship friendship = new Friendship();
        friendship.setFriendshipId(rs.getInt("friendship_id"));
        friendship.setUser1Id(rs.getInt("user1_id"));
        friendship.setUser2Id(rs.getInt("user2_id"));
        friendship.setStatus(rs.getString("status"));
        friendship.setCreatedAt(rs.getTimestamp("created_at"));
        friendship.setUpdatedAt(rs.getTimestamp("updated_at"));
        return friendship;
    }
}

