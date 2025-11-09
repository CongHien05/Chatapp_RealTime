package org.example.danbainoso.shared;

import org.example.danbainoso.shared.models.CallRequest;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface VideoClientCallback extends Remote {
    void onIncomingCall(CallRequest callRequest) throws RemoteException;
    void onCallAccepted(String callId) throws RemoteException;
    void onCallRejected(String callId) throws RemoteException;
    void onCallEnded(String callId) throws RemoteException;
}

