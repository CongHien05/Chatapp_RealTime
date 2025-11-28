package org.example.danbainoso.utils;

import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Utility class for network operations
 */
public class NetworkUtil {
    private static final Logger logger = LoggerUtil.getLogger(NetworkUtil.class);
    
    /**
     * Get the local IP address of this machine (not localhost)
     * Prioritizes non-localhost, non-virtual network interfaces
     * 
     * @return IP address as string, or "localhost" if not found
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // Skip loopback, down, and virtual interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                // Skip virtual interfaces (VirtualBox, VMware, etc.)
                String name = networkInterface.getName().toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || 
                    name.contains("vbox") || name.contains("docker")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // We want IPv4 addresses that are not loopback
                    if (!address.isLoopbackAddress() && 
                        !address.isLinkLocalAddress() && 
                        address.isSiteLocalAddress() &&
                        address.getHostAddress().indexOf(':') == -1) { // IPv4 only
                        
                        String ip = address.getHostAddress();
                        logger.info("Found local IP address: {} on interface: {}", ip, networkInterface.getName());
                        return ip;
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("Failed to get local IP address", e);
        }
        
        // Fallback to localhost
        logger.warn("Could not determine local IP address, using localhost");
        return "localhost";
    }
    
    /**
     * Get the hostname of this machine
     * 
     * @return hostname as string
     */
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            logger.error("Failed to get hostname", e);
            return "unknown";
        }
    }
}

