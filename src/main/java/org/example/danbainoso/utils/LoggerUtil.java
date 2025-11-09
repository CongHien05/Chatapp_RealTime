package org.example.danbainoso.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtil {
    
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    public static void logInfo(Class<?> clazz, String message) {
        getLogger(clazz).info(message);
    }
    
    public static void logError(Class<?> clazz, String message, Throwable throwable) {
        getLogger(clazz).error(message, throwable);
    }
    
    public static void logWarn(Class<?> clazz, String message) {
        getLogger(clazz).warn(message);
    }
    
    public static void logDebug(Class<?> clazz, String message) {
        getLogger(clazz).debug(message);
    }
}
