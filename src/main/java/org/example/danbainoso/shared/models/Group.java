package org.example.danbainoso.shared.models;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum GroupRole {
        ADMIN, MEMBER;

        public static GroupRole fromString(String value) {
            if (value == null) {
                return null;
            }
            try {
                return GroupRole.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
    
    private int groupId;
    private String groupName;
    private String description;
    private String avatarUrl;
    private int createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Transient fields
    private List<User> members;
    private int memberCount;
    private String creatorName;
    
    public Group() {
        this.members = new ArrayList<>();
    }
    
    public Group(String groupName, String description, int createdBy) {
        this.groupName = groupName;
        this.description = description;
        this.createdBy = createdBy;
        this.members = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getGroupId() {
        return groupId;
    }
    
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public int getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public List<User> getMembers() {
        return members;
    }
    
    public void setMembers(List<User> members) {
        this.members = members;
        this.memberCount = members != null ? members.size() : 0;
    }
    
    public int getMemberCount() {
        return memberCount;
    }
    
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    public String getCreatorName() {
        return creatorName;
    }
    
    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }
    
    public void addMember(User user) {
        if (members == null) {
            members = new ArrayList<>();
        }
        members.add(user);
        memberCount = members.size();
    }
    
    public void removeMember(User user) {
        if (members != null) {
            members.remove(user);
            memberCount = members.size();
        }
    }
    
    @Override
    public String toString() {
        return "Group{" +
                "groupId=" + groupId +
                ", groupName='" + groupName + '\'' +
                ", description='" + description + '\'' +
                ", createdBy=" + createdBy +
                ", memberCount=" + memberCount +
                '}';
    }
}
