package com.data4circ.portal.features.notification.service;

import com.data4circ.portal.features.notification.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages SSE emitter connections for real-time notifications.
 * Handles registration, cleanup, and message broadcasting.
 */
@Component
public class SseEmitterManager {

    private static final Logger logger = LoggerFactory.getLogger(SseEmitterManager.class);

    // User ID -> Set of emitters (user can have multiple browser tabs/devices)
    private final Map<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // Emitter -> User ID mapping for cleanup
    private final Map<SseEmitter, Long> emitterToUser = new ConcurrentHashMap<>();

    // SSE connection timeout (30 minutes)
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    // Heartbeat interval (30 seconds) to keep connections alive
    private static final long HEARTBEAT_INTERVAL = 30 * 1000L;

    // Maximum SSE connections per user (to handle multiple tabs, but prevent accumulation)
    private static final int MAX_CONNECTIONS_PER_USER = 3;

    /**
     * Register a new SSE emitter for a user.
     * Limits connections per user to prevent accumulation from rapid page navigation.
     */
    public SseEmitter register(Long userId) {
        // Close excess connections BEFORE creating new one to prevent accumulation
        closeExcessConnections(userId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Set up callbacks for cleanup
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            logger.debug("SSE connection timeout for user {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onError(e -> {
            logger.debug("SSE connection error for user {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        });

        // Add to maps
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitterToUser.put(emitter, userId);

        logger.info("Registered SSE emitter for user {}. Total connections: {}", userId, getConnectionCount());
        return emitter;
    }

    /**
     * Close excess connections for a user to prevent accumulation.
     * Keeps only (MAX_CONNECTIONS_PER_USER - 1) connections to make room for the new one.
     */
    private void closeExcessConnections(Long userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.size() < MAX_CONNECTIONS_PER_USER) {
            return;
        }

        int toClose = emitters.size() - MAX_CONNECTIONS_PER_USER + 1;
        logger.info("User {} has {} connections, closing {} oldest", userId, emitters.size(), toClose);

        int closed = 0;
        for (SseEmitter emitter : emitters) {
            if (closed >= toClose) break;
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.trace("Error completing excess emitter: {}", e.getMessage());
            }
            removeEmitter(userId, emitter);
            closed++;
        }
    }

    /**
     * Remove an emitter from tracking.
     */
    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
        emitterToUser.remove(emitter);
        logger.debug("Removed SSE emitter for user {}. Total connections: {}", userId, getConnectionCount());
    }

    /**
     * Send a notification to a specific user.
     */
    public void sendToUser(Long userId, NotificationEvent event) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            logger.debug("No active SSE connections for user {}", userId);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(event, MediaType.APPLICATION_JSON));
                logger.debug("Sent notification {} to user {}", event.getType(), userId);
            } catch (IOException | IllegalStateException e) {
                logger.debug("Failed to send notification to user {}: {} - removing emitter",
                    userId, e.getClass().getSimpleName());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // Ignore errors when completing - connection is already dead
                    logger.trace("Error completing emitter: {}", ex.getMessage());
                }
                removeEmitter(userId, emitter);
            }
        }
    }

    /**
     * Send a notification to multiple users.
     */
    public void sendToUsers(Set<Long> userIds, NotificationEvent event) {
        for (Long userId : userIds) {
            sendToUser(userId, event);
        }
    }

    /**
     * Broadcast a notification to all connected users.
     */
    public void broadcast(NotificationEvent event) {
        logger.info("Broadcasting notification {} to {} users", event.getType(), userEmitters.size());
        for (Long userId : userEmitters.keySet()) {
            sendToUser(userId, event);
        }
    }

    /**
     * Send a connection confirmation event.
     */
    public void sendConnectionEvent(SseEmitter emitter, long unreadCount) {
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of(
                    "status", "connected",
                    "unreadCount", unreadCount
                ), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            logger.warn("Failed to send connection event: {} - {}",
                e.getClass().getSimpleName(), e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                // Ignore - connection already dead
                logger.trace("Error completing emitter: {}", ex.getMessage());
            }
        }
    }

    /**
     * Send heartbeat to keep connections alive.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }

        logger.trace("Sending heartbeat to {} users", userEmitters.size());
        for (Map.Entry<Long, Set<SseEmitter>> entry : userEmitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Map.of("timestamp", System.currentTimeMillis())));
                } catch (IOException | IllegalStateException e) {
                    // Connection is dead (either IOException or response recycled)
                    logger.debug("Heartbeat failed for user {}: {} - removing emitter",
                        entry.getKey(), e.getClass().getSimpleName());
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        // Ignore errors when trying to complete - connection is already dead
                        logger.trace("Error completing emitter: {}", ex.getMessage());
                    }
                    // Manually remove the emitter to ensure cleanup
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    /**
     * Check if a user has any active connections.
     */
    public boolean isUserConnected(Long userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    /**
     * Get the total number of active connections.
     */
    public int getConnectionCount() {
        return emitterToUser.size();
    }

    /**
     * Get the number of connected users.
     */
    public int getConnectedUserCount() {
        return userEmitters.size();
    }

    /**
     * Get connection statistics.
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "connectedUsers", getConnectedUserCount(),
            "totalConnections", getConnectionCount()
        );
    }
}
