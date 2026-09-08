package com.data4circ.portal.features.notification.repository;

import com.data4circ.portal.features.organization.entity.User;
import com.data4circ.portal.features.notification.entity.Notification;
import com.data4circ.portal.features.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications for a user, ordered by creation time (newest first).
     */
    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);

    /**
     * Find notifications for a user with pagination.
     */
    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    /**
     * Find unread notifications for a user.
     */
    List<Notification> findByRecipientAndReadFalseOrderByCreatedAtDesc(User recipient);

    /**
     * Count unread notifications for a user.
     */
    long countByRecipientAndReadFalse(User recipient);

    /**
     * Find notifications by type for a user.
     */
    List<Notification> findByRecipientAndTypeOrderByCreatedAtDesc(User recipient, NotificationType type);

    /**
     * Find recent notifications (last N) for a user.
     */
    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient ORDER BY n.createdAt DESC")
    List<Notification> findRecentByRecipient(@Param("recipient") User recipient, Pageable pageable);

    /**
     * Mark all notifications as read for a user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.recipient = :recipient AND n.read = false")
    int markAllAsReadForUser(@Param("recipient") User recipient, @Param("now") LocalDateTime now);

    /**
     * Delete old read notifications (for cleanup).
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :before")
    int deleteOldReadNotifications(@Param("before") LocalDateTime before);

    /**
     * Find notifications by recipient ID.
     */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /**
     * Count unread notifications by recipient ID.
     */
    long countByRecipientIdAndReadFalse(Long recipientId);

    /**
     * Delete all notifications for a recipient. Used when a user is deleted, since the
     * recipient FK is non-nullable.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :recipientId")
    void deleteByRecipientId(@Param("recipientId") Long recipientId);
}
