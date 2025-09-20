package com.nsebot.service;

import com.nsebot.entity.User;
import com.nsebot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user operations, preferences, and rate limiting
 */
@Service
@Transactional
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Register or update user from Telegram update
     */
    public User registerOrUpdateUser(Update update) {
        org.telegram.telegrambots.meta.api.objects.User telegramUser;
        
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            telegramUser = update.getMessage().getFrom();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            telegramUser = update.getCallbackQuery().getFrom();
        } else {
            logger.warn("Unable to extract user information from update");
            return null;
        }
        
        Long telegramId = telegramUser.getId();
        Optional<User> existingUser = userRepository.findByTelegramId(telegramId);
        
        if (existingUser.isPresent()) {
            // Update existing user information
            User user = existingUser.get();
            boolean updated = false;
            
            // Handle null usernames safely
            String newUsername = telegramUser.getUserName();
            if ((user.getUsername() == null && newUsername != null) || 
                (user.getUsername() != null && !user.getUsername().equals(newUsername))) {
                user.setUsername(telegramUser.getUserName());
                updated = true;
            }
            
            if (!user.getFirstName().equals(telegramUser.getFirstName())) {
                user.setFirstName(telegramUser.getFirstName());
                updated = true;
            }
            
            if (!user.getLastName().equals(telegramUser.getLastName())) {
                user.setLastName(telegramUser.getLastName());
                updated = true;
            }
            
            if (updated) {
                user = userRepository.save(user);
                logger.info("Updated user information for Telegram ID: {}", telegramId);
            }
            
            return user;
        } else {
            // Create new user
            User newUser = new User(
                telegramId,
                telegramUser.getUserName(),
                telegramUser.getFirstName(),
                telegramUser.getLastName()
            );
            
            newUser = userRepository.save(newUser);
            logger.info("Registered new user: {} (Telegram ID: {})", newUser.getDisplayName(), telegramId);
            return newUser;
        }
    }
    
    /**
     * Get user by Telegram ID with caching
     */
    @Cacheable(value = "users", key = "#telegramId")
    public Optional<User> getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }
    
    /**
     * Check if user can make a request (rate limiting)
     */
    public boolean canUserMakeRequest(Long telegramId) {
        Optional<User> userOpt = getUserByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            return false;
        }
        
        return user.canMakeRequest();
    }
    
    /**
     * Record user request and update rate limiting counters
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public void recordUserRequest(Long telegramId) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.incrementRequestCount();
            userRepository.save(user);
        }
    }
    
    /**
     * Add symbol to user's watchlist
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public boolean addToWatchlist(Long telegramId, String symbol) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.addToWatchlist(symbol);
            userRepository.save(user);
            logger.info("Added {} to watchlist for user {}", symbol, user.getDisplayName());
            return true;
        }
        return false;
    }
    
    /**
     * Remove symbol from user's watchlist
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public boolean removeFromWatchlist(Long telegramId, String symbol) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.removeFromWatchlist(symbol);
            userRepository.save(user);
            logger.info("Removed {} from watchlist for user {}", symbol, user.getDisplayName());
            return true;
        }
        return false;
    }
    
    /**
     * Get user's watchlist
     */
    public List<String> getUserWatchlist(Long telegramId) {
        Optional<User> userOpt = getUserByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            return userOpt.get().getWatchlist().stream().toList();
        }
        return List.of();
    }
    
    /**
     * Update user preferences
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public boolean updateUserPreferences(Long telegramId, String language, String timezone, boolean notifications) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPreferredLanguage(language);
            user.setTimezone(timezone);
            user.setNotificationsEnabled(notifications);
            userRepository.save(user);
            logger.info("Updated preferences for user {}", user.getDisplayName());
            return true;
        }
        return false;
    }
    
    /**
     * Upgrade user subscription
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public boolean upgradeUserSubscription(Long telegramId, User.SubscriptionTier newTier) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            User.SubscriptionTier oldTier = user.getSubscriptionTier();
            user.setSubscriptionTier(newTier);
            userRepository.save(user);
            logger.info("Upgraded user {} from {} to {}", user.getDisplayName(), oldTier, newTier);
            return true;
        }
        return false;
    }
    
    /**
     * Get users watching a specific symbol for notifications
     */
    public List<User> getUsersWatchingSymbol(String symbol) {
        return userRepository.findUsersWatchingSymbol(symbol.toUpperCase());
    }
    
    /**
     * Get user statistics
     */
    public UserStatistics getUserStatistics() {
        Object[] stats = userRepository.getUserStatistics();
        
        if (stats.length > 0) {
            Object[] row = stats;
            return new UserStatistics(
                ((Number) row[0]).longValue(),  // totalUsers
                ((Number) row[1]).longValue(),  // activeUsers
                ((Number) row[2]).longValue(),  // premiumUsers
                ((Number) row[3]).longValue()   // proUsers
            );
        }
        
        return new UserStatistics(0, 0, 0, 0);
    }
    
    /**
     * Ban or unban user
     */
    @CacheEvict(value = "users", key = "#telegramId")
    public boolean updateUserStatus(Long telegramId, User.UserStatus status) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStatus(status);
            userRepository.save(user);
            logger.info("Updated status for user {} to {}", user.getDisplayName(), status);
            return true;
        }
        return false;
    }
    
    /**
     * Reset daily request counts for all users (scheduled task)
     */
    @Scheduled(cron = "0 0 0 * * ?") // Every day at midnight
    public void resetDailyRequestCounts() {
        LocalDateTime resetTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        int updatedCount = userRepository.resetDailyRequestCounts(resetTime);
        logger.info("Reset daily request counts for {} users", updatedCount);
    }
    
    /**
     * Clean up inactive users (scheduled task)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    public void cleanupInactiveUsers() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90); // 90 days inactive
        List<User> inactiveUsers = userRepository.findInactiveUsers(cutoffDate);
        
        for (User user : inactiveUsers) {
            if (user.getStatus() == User.UserStatus.ACTIVE) {
                user.setStatus(User.UserStatus.INACTIVE);
                userRepository.save(user);
            }
        }
        
        logger.info("Marked {} users as inactive", inactiveUsers.size());
    }
    
    /**
     * User statistics data class
     */
    public static class UserStatistics {
        private final long totalUsers;
        private final long activeUsers;
        private final long premiumUsers;
        private final long proUsers;
        
        public UserStatistics(long totalUsers, long activeUsers, long premiumUsers, long proUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.premiumUsers = premiumUsers;
            this.proUsers = proUsers;
        }
        
        public long getTotalUsers() { return totalUsers; }
        public long getActiveUsers() { return activeUsers; }
        public long getPremiumUsers() { return premiumUsers; }
        public long getProUsers() { return proUsers; }
        public long getFreeUsers() { return activeUsers - premiumUsers - proUsers; }
        
        @Override
        public String toString() {
            return String.format("UserStats{total=%d, active=%d, premium=%d, pro=%d, free=%d}", 
                               totalUsers, activeUsers, premiumUsers, proUsers, getFreeUsers());
        }
    }
}