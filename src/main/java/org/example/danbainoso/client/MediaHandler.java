package org.example.danbainoso.client;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.example.danbainoso.utils.LoggerUtil;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MediaHandler {
    private static final Logger logger = LoggerUtil.getLogger(MediaHandler.class);
    private static final String MEDIA_DIR = "media";
    private MediaPlayer notificationPlayer;
    
    public MediaHandler() {
        createMediaDirectory();
    }
    
    private void createMediaDirectory() {
        try {
            Path mediaPath = Paths.get(MEDIA_DIR);
            if (!Files.exists(mediaPath)) {
                Files.createDirectories(mediaPath);
            }
        } catch (Exception e) {
            logger.error("Failed to create media directory", e);
        }
    }
    
    public void playNotificationSound() {
        try {
            // You can add a notification sound file here
            // For now, this is a placeholder
            logger.debug("Playing notification sound");
        } catch (Exception e) {
            logger.error("Failed to play notification sound", e);
        }
    }
    
    public void stopNotificationSound() {
        if (notificationPlayer != null) {
            notificationPlayer.stop();
        }
    }
    
    public String saveMediaFile(byte[] data, String filename) {
        try {
            Path filePath = Paths.get(MEDIA_DIR, filename);
            Files.write(filePath, data);
            logger.info("Media file saved: {}", filePath);
            return filePath.toString();
        } catch (Exception e) {
            logger.error("Failed to save media file", e);
            return null;
        }
    }
    
    public byte[] loadMediaFile(String filepath) {
        try {
            Path filePath = Paths.get(filepath);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
        } catch (Exception e) {
            logger.error("Failed to load media file", e);
        }
        return null;
    }
    
    public boolean deleteMediaFile(String filepath) {
        try {
            Path filePath = Paths.get(filepath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Media file deleted: {}", filePath);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to delete media file", e);
        }
        return false;
    }
}
