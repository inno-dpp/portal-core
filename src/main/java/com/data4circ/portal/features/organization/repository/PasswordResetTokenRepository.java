package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.PasswordResetToken;
import com.data4circ.portal.features.organization.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Find a valid (unused) token by its value.
     */
    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);

    /**
     * Count recent password reset requests for rate limiting.
     */
    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.user.email = :email AND t.createdAt > :since")
    long countRecentRequestsByEmail(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Invalidate all existing tokens for a user (mark as used).
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllTokensForUser(@Param("user") User user);

    /**
     * Delete expired tokens for cleanup.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    void deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete all reset tokens for a user. Used when a user is deleted, since the user FK is
     * non-nullable.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
