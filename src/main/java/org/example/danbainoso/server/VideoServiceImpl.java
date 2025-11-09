package org.example.danbainoso.server;

import org.example.danbainoso.shared.VideoClientCallback;
import org.example.danbainoso.shared.VideoService;
import org.example.danbainoso.shared.models.CallRequest;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class VideoServiceImpl extends UnicastRemoteObject implements VideoService {
    private static final Logger logger = LoggerUtil.getLogger(VideoServiceImpl.class);
    
    private final ConcurrentHashMap<String, CallRequest> activeCalls;
    private final ConcurrentHashMap<String, VideoClientCallback> clients;
    
    public VideoServiceImpl() throws RemoteException {
        super();
        this.activeCalls = new ConcurrentHashMap<>();
        this.clients = new ConcurrentHashMap<>();
    }
    
    @Override
    public CallRequest initiateCall(CallRequest callRequest) throws RemoteException {
        activeCalls.put(callRequest.getCallId(), callRequest);
        logger.info("Call initiated: {} from {} to {}", 
                callRequest.getCallId(), 
                callRequest.getCallerId(), 
                callRequest.getReceiverId());
        
        // Notify receiver
        String receiverKey = "user_" + callRequest.getReceiverId();
        VideoClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onIncomingCall(callRequest);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver of incoming call", e);
                clients.remove(receiverKey);
            }
        }
        
        return callRequest;
    }
    
    @Override
    public boolean acceptCall(String callId, int userId) throws RemoteException {
        CallRequest callRequest = activeCalls.get(callId);
        if (callRequest == null) {
            logger.warn("Call not found: {}", callId);
            return false;
        }
        
        if (callRequest.getReceiverId() != userId && callRequest.getCallerId() != userId) {
            logger.warn("User {} is not authorized to accept call {}", userId, callId);
            return false;
        }
        
        callRequest.setStatus(CallRequest.CallStatus.ACCEPTED);
        callRequest.setAnsweredAt(new java.sql.Timestamp(System.currentTimeMillis()));
        activeCalls.put(callId, callRequest);
        
        logger.info("Call accepted: {} by user {}", callId, userId);
        
        // Notify both parties
        notifyCallAccepted(callId, callRequest);
        
        return true;
    }
    
    @Override
    public boolean rejectCall(String callId, int userId) throws RemoteException {
        CallRequest callRequest = activeCalls.get(callId);
        if (callRequest == null) {
            logger.warn("Call not found: {}", callId);
            return false;
        }
        
        if (callRequest.getReceiverId() != userId && callRequest.getCallerId() != userId) {
            logger.warn("User {} is not authorized to reject call {}", userId, callId);
            return false;
        }
        
        callRequest.setStatus(CallRequest.CallStatus.REJECTED);
        activeCalls.put(callId, callRequest);
        
        logger.info("Call rejected: {} by user {}", callId, userId);
        
        // Notify both parties
        notifyCallRejected(callId, callRequest);
        
        // Remove from active calls after a delay
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                activeCalls.remove(callId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return true;
    }
    
    @Override
    public boolean endCall(String callId, int userId) throws RemoteException {
        CallRequest callRequest = activeCalls.get(callId);
        if (callRequest == null) {
            logger.warn("Call not found: {}", callId);
            return false;
        }
        
        if (callRequest.getCallerId() != userId && callRequest.getReceiverId() != userId) {
            logger.warn("User {} is not authorized to end call {}", userId, callId);
            return false;
        }
        
        callRequest.setStatus(CallRequest.CallStatus.ENDED);
        callRequest.setEndedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        
        logger.info("Call ended: {} by user {}", callId, userId);
        
        // Notify both parties
        notifyCallEnded(callId, callRequest);
        
        // Remove from active calls
        activeCalls.remove(callId);
        
        return true;
    }
    
    @Override
    public CallRequest getCallRequest(String callId) throws RemoteException {
        return activeCalls.get(callId);
    }
    
    @Override
    public void registerCallClient(String clientId, VideoClientCallback callback) throws RemoteException {
        clients.put(clientId, callback);
        logger.info("Call client registered: {}", clientId);
    }
    
    @Override
    public void unregisterCallClient(String clientId) throws RemoteException {
        clients.remove(clientId);
        logger.info("Call client unregistered: {}", clientId);
    }
    
    // Notification methods
    private void notifyCallAccepted(String callId, CallRequest callRequest) {
        String callerKey = "user_" + callRequest.getCallerId();
        String receiverKey = "user_" + callRequest.getReceiverId();
        
        VideoClientCallback caller = clients.get(callerKey);
        if (caller != null) {
            try {
                caller.onCallAccepted(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify caller", e);
                clients.remove(callerKey);
            }
        }
        
        VideoClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onCallAccepted(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver", e);
                clients.remove(receiverKey);
            }
        }
    }
    
    private void notifyCallRejected(String callId, CallRequest callRequest) {
        String callerKey = "user_" + callRequest.getCallerId();
        String receiverKey = "user_" + callRequest.getReceiverId();
        
        VideoClientCallback caller = clients.get(callerKey);
        if (caller != null) {
            try {
                caller.onCallRejected(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify caller", e);
                clients.remove(callerKey);
            }
        }
        
        VideoClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onCallRejected(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver", e);
                clients.remove(receiverKey);
            }
        }
    }
    
    private void notifyCallEnded(String callId, CallRequest callRequest) {
        String callerKey = "user_" + callRequest.getCallerId();
        String receiverKey = "user_" + callRequest.getReceiverId();
        
        VideoClientCallback caller = clients.get(callerKey);
        if (caller != null) {
            try {
                caller.onCallEnded(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify caller", e);
                clients.remove(callerKey);
            }
        }
        
        VideoClientCallback receiver = clients.get(receiverKey);
        if (receiver != null) {
            try {
                receiver.onCallEnded(callId);
            } catch (RemoteException e) {
                logger.error("Failed to notify receiver", e);
                clients.remove(receiverKey);
            }
        }
    }
}
