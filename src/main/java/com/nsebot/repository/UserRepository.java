package com.nsebot.repository;

import com.nsebot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by Telegram ID
     */
    Optional<User> findByTelegramId(Long telegramId);
    
    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find all active users
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE'")
    List<User> findAllActiveUsers();
    
    /**
     * Find users by subscription tier
     */
    List<User> findBySubscriptionTier(User.SubscriptionTier tier);
    
    /**
     * Find users with notifications enabled
     */
    @Query("SELECT u FROM User u WHERE u.notificationsEnabled = true AND u.status = 'ACTIVE'")
    List<User> findUsersWithNotificationsEnabled();
    
    /**
     * Find users watching a specific symbol
     */
    @Query("SELECT u FROM User u JOIN u.watchlist w WHERE w = :symbol AND u.status = 'ACTIVE'")
    List<User> findUsersWatchingSymbol(@Param("symbol") String symbol);
    
    /**
     * Count users by subscription tier
     */
    long countBySubscriptionTier(User.SubscriptionTier tier);
    
    /**
     * Find users created within date range
     */
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    List<User> findUsersCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find users who exceeded daily limit
     */
    @Query("SELECT u FROM User u WHERE u.dailyRequestCount >= " +
           "(CASE u.subscriptionTier " +
           "WHEN 'FREE' THEN 10 " +
           "WHEN 'PREMIUM' THEN 100 " +
           "WHEN 'PRO' THEN 500 END)")
    List<User> findUsersWhoExceededDailyLimit();
    
    /**
     * Find inactive users (no requests in last N days)
     */
    @Query("SELECT u FROM User u WHERE u.lastRequestTime < :cutoffDate OR u.lastRequestTime IS NULL")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Update user's last request time and increment count
     */
    @Modifying
    @Query("UPDATE User u SET u.lastRequestTime = :requestTime, u.dailyRequestCount = u.dailyRequestCount + 1 " +
           "WHERE u.telegramId = :telegramId")
    int incrementUserRequestCount(@Param("telegramId") Long telegramId, 
                                  @Param("requestTime") LocalDateTime requestTime);
    
    /**
     * Reset daily request counts for all users
     */
    @Modifying
    @Query("UPDATE User u SET u.dailyRequestCount = 0, u.dailyLimitResetTime = :resetTime")
    int resetDailyRequestCounts(@Param("resetTime") LocalDateTime resetTime);
    
    /**
     * Update user subscription tier
     */
    @Modifying
    @Query("UPDATE User u SET u.subscriptionTier = :tier WHERE u.telegramId = :telegramId")
    int updateUserSubscriptionTier(@Param("telegramId") Long telegramId, 
                                   @Param("tier") User.SubscriptionTier tier);
    
    /**
     * Get user statistics
     */
    @Query("SELECT " +
           "COUNT(*) as totalUsers, " +
           "SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END) as activeUsers, " +
           "SUM(CASE WHEN u.subscriptionTier = 'PREMIUM' THEN 1 ELSE 0 END) as premiumUsers, " +
           "SUM(CASE WHEN u.subscriptionTier = 'PRO' THEN 1 ELSE 0 END) as proUsers " +
           "FROM User u")
    Object[] getUserStatistics();
    
    /**
     * Find top users by request count
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' ORDER BY u.dailyRequestCount DESC")
    List<User> findTopUsersByRequestCount();
}