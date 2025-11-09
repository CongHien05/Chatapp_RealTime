package org.example.danbainoso.shared;

import org.example.danbainoso.shared.models.CallRequest;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface VideoService extends Remote {
    
    // Call operations
    CallRequest initiateCall(CallRequest callRequest) throws RemoteException;
    boolean acceptCall(String callId, int userId) throws RemoteException;
    boolean rejectCall(String callId, int userId) throws RemoteException;
    boolean endCall(String callId, int userId) throws RemoteException;
    CallRequest getCallRequest(String callId) throws RemoteException;
    
    // Callback registration for real-time call updates
    void registerCallClient(String clientId, VideoClientCallback callback) throws RemoteException;
    void unregisterCallClient(String clientId) throws RemoteException;
}
