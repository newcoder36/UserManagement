package com.nsebot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity for advanced user management
 * Tracks user preferences, activity, and portfolio information
 */
@Entity
@Table(name = "users")
public class User {
    
    @Id
    private Long telegramId;
    
    @Column(nullable = true)  // Username can be null if Telegram user doesn't have one
    private String username;
    
    @Column(nullable = false)
    private String firstName;
    
    private String lastName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;
    
    @Column(nullable = false)
    private boolean notificationsEnabled = true;
    
    @Column(nullable = false)
    private String preferredLanguage = "en";
    
    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";
    
    @ElementCollection
    @CollectionTable(name = "user_watchlist", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "symbol")
    private Set<String> watchlist = new HashSet<>();
    
    @Column(nullable = false)
    private int dailyRequestCount = 0;
    
    @Column
    private LocalDateTime lastRequestTime;
    
    @Column
    private LocalDateTime dailyLimitResetTime;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public User() {}
    
    public User(Long telegramId, String username, String firstName, String lastName) {
        this.telegramId = telegramId;
        this.username = username; // Can be null if user doesn't have a Telegram username
        this.firstName = firstName;
        this.lastName = lastName;
        this.dailyLimitResetTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
    }
    
    // Getters and Setters
    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    
    public SubscriptionTier getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(SubscriptionTier subscriptionTier) { this.subscriptionTier = subscriptionTier; }
    
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    
    public Set<String> getWatchlist() { return watchlist; }
    public void setWatchlist(Set<String> watchlist) { this.watchlist = watchlist; }
    
    public int getDailyRequestCount() { return dailyRequestCount; }
    public void setDailyRequestCount(int dailyRequestCount) { this.dailyRequestCount = dailyRequestCount; }
    
    public LocalDateTime getLastRequestTime() { return lastRequestTime; }
    public void setLastRequestTime(LocalDateTime lastRequestTime) { this.lastRequestTime = lastRequestTime; }
    
    public LocalDateTime getDailyLimitResetTime() { return dailyLimitResetTime; }
    public void setDailyLimitResetTime(LocalDateTime dailyLimitResetTime) { this.dailyLimitResetTime = dailyLimitResetTime; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    // Business methods
    public boolean canMakeRequest() {
        resetDailyCountIfNeeded();
        return dailyRequestCount < getSubscriptionTier().getDailyLimit();
    }
    
    public void incrementRequestCount() {
        resetDailyCountIfNeeded();
        this.dailyRequestCount++;
        this.lastRequestTime = LocalDateTime.now();
    }
    
    private void resetDailyCountIfNeeded() {
        if (LocalDateTime.now().isAfter(dailyLimitResetTime)) {
            this.dailyRequestCount = 0;
            this.dailyLimitResetTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        }
    }
    
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (username != null) {
            return "@" + username;
        } else {
            return "User #" + telegramId;
        }
    }
    
    public void addToWatchlist(String symbol) {
        this.watchlist.add(symbol.toUpperCase());
    }
    
    public void removeFromWatchlist(String symbol) {
        this.watchlist.remove(symbol.toUpperCase());
    }
    
    public boolean isWatching(String symbol) {
        return this.watchlist.contains(symbol.toUpperCase());
    }
    
    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', firstName='%s', tier=%s}", 
                           telegramId, username, firstName, subscriptionTier);
    }
    
    // Enums
    public enum UserStatus {
        ACTIVE, INACTIVE, BANNED
    }
    
    public enum SubscriptionTier {
        FREE(10, "Free"),
        PREMIUM(100, "Premium"),
        PRO(500, "Pro");
        
        private final int dailyLimit;
        private final String displayName;
        
        SubscriptionTier(int dailyLimit, String displayName) {
            this.dailyLimit = dailyLimit;
            this.displayName = displayName;
        }
        
        public int getDailyLimit() { return dailyLimit; }
        public String getDisplayName() { return displayName; }
    }
}