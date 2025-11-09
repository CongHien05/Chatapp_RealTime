package org.example.danbainoso.shared.models;

import java.io.Serializable;
import java.sql.Timestamp;

public class CallRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String callId;
    private int callerId;
    private int receiverId;
    private Integer groupId; // null for private calls
    private CallType callType;
    private CallStatus status;
    private Timestamp createdAt;
    private Timestamp answeredAt;
    private Timestamp endedAt;
    
    // Transient fields
    private String callerName;
    private String receiverName;
    
    public enum CallType {
        VOICE, VIDEO
    }
    
    public enum CallStatus {
        PENDING, ACCEPTED, REJECTED, ENDED, MISSED
    }
    
    public CallRequest() {
        this.status = CallStatus.PENDING;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    public CallRequest(String callId, int callerId, int receiverId, CallType callType) {
        this.callId = callId;
        this.callerId = callerId;
        this.receiverId = receiverId;
        this.callType = callType;
        this.status = CallStatus.PENDING;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    // Getters and Setters
    public String getCallId() {
        return callId;
    }
    
    public void setCallId(String callId) {
        this.callId = callId;
    }
    
    public int getCallerId() {
        return callerId;
    }
    
    public void setCallerId(int callerId) {
        this.callerId = callerId;
    }
    
    public int getReceiverId() {
        return receiverId;
    }
    
    public void setReceiverId(int receiverId) {
        this.receiverId = receiverId;
    }
    
    public Integer getGroupId() {
        return groupId;
    }
    
    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }
    
    public CallType getCallType() {
        return callType;
    }
    
    public void setCallType(CallType callType) {
        this.callType = callType;
    }
    
    public CallStatus getStatus() {
        return status;
    }
    
    public void setStatus(CallStatus status) {
        this.status = status;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getAnsweredAt() {
        return answeredAt;
    }
    
    public void setAnsweredAt(Timestamp answeredAt) {
        this.answeredAt = answeredAt;
    }
    
    public Timestamp getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(Timestamp endedAt) {
        this.endedAt = endedAt;
    }
    
    public String getCallerName() {
        return callerName;
    }
    
    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }
    
    public String getReceiverName() {
        return receiverName;
    }
    
    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }
    
    public boolean isGroupCall() {
        return groupId != null;
    }
    
    @Override
    public String toString() {
        return "CallRequest{" +
                "callId='" + callId + '\'' +
                ", callerId=" + callerId +
                ", receiverId=" + receiverId +
                ", groupId=" + groupId +
                ", callType=" + callType +
                ", status=" + status +
                '}';
    }
}
