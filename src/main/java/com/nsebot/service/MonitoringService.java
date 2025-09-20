package com.nsebot.service;

import com.nsebot.entity.User;
import com.nsebot.repository.UserRepository;
import com.nsebot.repository.PortfolioRepository;
import com.nsebot.repository.StockAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced monitoring service for system health, performance metrics, and alerting
 */
@Service
public class MonitoringService implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private StockAnalysisRepository stockAnalysisRepository;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private CacheManagementService cacheManagementService;
    
    @Autowired
    private UserService userService;
    
    // Performance metrics
    private long totalRequests = 0;
    private long successfulRequests = 0;
    private long failedRequests = 0;
    private double averageResponseTime = 0.0;
    private LocalDateTime lastHealthCheck = LocalDateTime.now();
    
    /**
     * Comprehensive health check for Spring Boot Actuator
     */
    @Override
    public Health health() {
        try {
            SystemHealthMetrics metrics = performHealthCheck();
            
            Health.Builder healthBuilder = Health.up()
                .withDetail("timestamp", LocalDateTime.now())
                .withDetail("database", metrics.isDatabaseHealthy() ? "UP" : "DOWN")
                .withDetail("cache", metrics.isCacheHealthy() ? "UP" : "DOWN")
                .withDetail("telegram", metrics.isTelegramHealthy() ? "UP" : "DOWN")
                .withDetail("totalUsers", metrics.getTotalUsers())
                .withDetail("activeUsers", metrics.getActiveUsers())
                .withDetail("totalPortfolios", metrics.getTotalPortfolios())
                .withDetail("requestSuccessRate", metrics.getRequestSuccessRate())
                .withDetail("averageResponseTime", metrics.getAverageResponseTime());
            
            if (!metrics.isSystemHealthy()) {
                healthBuilder = Health.down()
                    .withDetail("issues", metrics.getHealthIssues());
            }
            
            this.lastHealthCheck = LocalDateTime.now();
            return healthBuilder.build();
            
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Perform comprehensive system health check
     */
    public SystemHealthMetrics performHealthCheck() {
        SystemHealthMetrics metrics = new SystemHealthMetrics();
        
        try {
            // Database health check
            metrics.setTotalUsers(userRepository.count());
            metrics.setActiveUsers(userRepository.findAllActiveUsers().size());
            metrics.setTotalPortfolios(portfolioRepository.count());
            metrics.setTotalAnalyses(stockAnalysisRepository.count());
            metrics.setDatabaseHealthy(true);
            
        } catch (Exception e) {
            logger.error("Database health check failed: {}", e.getMessage());
            metrics.setDatabaseHealthy(false);
            metrics.addHealthIssue("Database connection error: " + e.getMessage());
        }
        
        try {
            // Cache health check
            Map<String, Object> cacheHealth = cacheManagementService.getCacheHealth();
            logger.info("Cache health check result: {}", cacheHealth);
            
            String overallHealth = (String) cacheHealth.get("overallHealth");
            logger.info("Overall cache health: {}", overallHealth);
            
            metrics.setCacheHealthy("HEALTHY".equals(overallHealth));
            
            if (!metrics.isCacheHealthy()) {
                String errorMessage = (String) cacheHealth.get("error");
                logger.warn("Cache is unhealthy. Error: {}", errorMessage);
                metrics.addHealthIssue("Cache health issue: " + (errorMessage != null ? errorMessage : "Overall health: " + overallHealth));
            }
            
        } catch (Exception e) {
            logger.error("Cache health check failed: {}", e.getMessage());
            metrics.setCacheHealthy(false);
            metrics.addHealthIssue("Cache health check error: " + e.getMessage());
        }
        
        // Performance metrics
        metrics.setRequestSuccessRate(totalRequests > 0 ? 
            (double) successfulRequests / totalRequests * 100 : 100.0);
        metrics.setAverageResponseTime(averageResponseTime);
        
        // Telegram bot health (simplified)
        metrics.setTelegramHealthy(true); // Assume healthy if no exceptions
        
        return metrics;
    }
    
    /**
     * Get detailed system metrics
     */
    public Map<String, Object> getDetailedMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // User metrics
            UserService.UserStatistics userStats = userService.getUserStatistics();
            Map<String, Object> userMetrics = new HashMap<>();
            userMetrics.put("total", userStats.getTotalUsers());
            userMetrics.put("active", userStats.getActiveUsers());
            userMetrics.put("premium", userStats.getPremiumUsers());
            userMetrics.put("pro", userStats.getProUsers());
            userMetrics.put("free", userStats.getFreeUsers());
            metrics.put("users", userMetrics);
            
            // Portfolio metrics
            Map<String, Object> portfolioMetrics = new HashMap<>();
            portfolioMetrics.put("total", portfolioRepository.count());
            portfolioMetrics.put("active", portfolioRepository.countByUserIdAndStatus(null, null)); // Simplified
            metrics.put("portfolios", portfolioMetrics);
            
            // Analysis metrics
            Map<String, Object> analysisMetrics = new HashMap<>();
            analysisMetrics.put("total", stockAnalysisRepository.count());
            analysisMetrics.put("recent24h", getRecentAnalysesCount(24));
            metrics.put("analyses", analysisMetrics);
            
            // Cache metrics
            metrics.put("cache", cacheManagementService.getCacheStatistics());
            
            // Performance metrics
            Map<String, Object> performanceMetrics = new HashMap<>();
            performanceMetrics.put("totalRequests", totalRequests);
            performanceMetrics.put("successfulRequests", successfulRequests);
            performanceMetrics.put("failedRequests", failedRequests);
            performanceMetrics.put("successRate", totalRequests > 0 ? 
                (double) successfulRequests / totalRequests * 100 : 100.0);
            performanceMetrics.put("averageResponseTime", averageResponseTime);
            metrics.put("performance", performanceMetrics);
            
        } catch (Exception e) {
            logger.error("Error collecting detailed metrics: {}", e.getMessage());
            metrics.put("error", "Failed to collect metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Record successful request
     */
    public void recordSuccessfulRequest(long responseTimeMs) {
        totalRequests++;
        successfulRequests++;
        updateAverageResponseTime(responseTimeMs);
    }
    
    /**
     * Record failed request
     */
    public void recordFailedRequest(long responseTimeMs) {
        totalRequests++;
        failedRequests++;
        updateAverageResponseTime(responseTimeMs);
    }
    
    private void updateAverageResponseTime(long responseTimeMs) {
        // Simple moving average calculation
        averageResponseTime = ((averageResponseTime * (totalRequests - 1)) + responseTimeMs) / totalRequests;
    }
    
    /**
     * Generate system alerts based on metrics
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void generateSystemAlerts() {
        try {
            SystemHealthMetrics metrics = performHealthCheck();
            
            // Check for critical issues
            if (!metrics.isSystemHealthy()) {
                logger.error("SYSTEM ALERT: System health check failed");
                for (Map.Entry<String, String> entry : metrics.getHealthIssues().entrySet()) {
                    logger.error("Health Issue: {}", entry.getValue());
                }
            }
            
            // Check success rate
            if (metrics.getRequestSuccessRate() < 95.0) {
                logger.warn("PERFORMANCE ALERT: Request success rate dropped to {}%", 
                           metrics.getRequestSuccessRate());
            }
            
            // Check response time
            if (metrics.getAverageResponseTime() > 5000) { // 5 seconds
                logger.warn("PERFORMANCE ALERT: Average response time is {}ms", 
                           metrics.getAverageResponseTime());
            }
            
            // Check user activity
            if (metrics.getActiveUsers() == 0) {
                logger.warn("USER ALERT: No active users in the system");
            }
            
            // Check for rate limit violations
            List<User> usersExceededLimit = userRepository.findUsersWhoExceededDailyLimit();
            if (!usersExceededLimit.isEmpty()) {
                logger.info("RATE LIMIT: {} users exceeded daily request limits", 
                           usersExceededLimit.size());
            }
            
        } catch (Exception e) {
            logger.error("Error generating system alerts: {}", e.getMessage());
        }
    }
    
    /**
     * Reset performance metrics (scheduled daily)
     */
    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    public void resetPerformanceMetrics() {
        totalRequests = 0;
        successfulRequests = 0;
        failedRequests = 0;
        averageResponseTime = 0.0;
        logger.info("Performance metrics reset for new day");
    }
    
    private long getRecentAnalysesCount(int hours) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
            return stockAnalysisRepository.countByAnalyzedAtAfter(cutoff);
        } catch (Exception e) {
            logger.error("Error getting recent analyses count: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * System health metrics data class
     */
    public static class SystemHealthMetrics {
        private long totalUsers = 0;
        private long activeUsers = 0;
        private long totalPortfolios = 0;
        private long totalAnalyses = 0;
        private boolean databaseHealthy = false;
        private boolean cacheHealthy = false;
        private boolean telegramHealthy = false;
        private double requestSuccessRate = 0.0;
        private double averageResponseTime = 0.0;
        private final Map<String, String> healthIssues = new HashMap<>();
        
        // Getters and setters
        public long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
        
        public long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }
        
        public long getTotalPortfolios() { return totalPortfolios; }
        public void setTotalPortfolios(long totalPortfolios) { this.totalPortfolios = totalPortfolios; }
        
        public long getTotalAnalyses() { return totalAnalyses; }
        public void setTotalAnalyses(long totalAnalyses) { this.totalAnalyses = totalAnalyses; }
        
        public boolean isDatabaseHealthy() { return databaseHealthy; }
        public void setDatabaseHealthy(boolean databaseHealthy) { this.databaseHealthy = databaseHealthy; }
        
        public boolean isCacheHealthy() { return cacheHealthy; }
        public void setCacheHealthy(boolean cacheHealthy) { this.cacheHealthy = cacheHealthy; }
        
        public boolean isTelegramHealthy() { return telegramHealthy; }
        public void setTelegramHealthy(boolean telegramHealthy) { this.telegramHealthy = telegramHealthy; }
        
        public double getRequestSuccessRate() { return requestSuccessRate; }
        public void setRequestSuccessRate(double requestSuccessRate) { this.requestSuccessRate = requestSuccessRate; }
        
        public double getAverageResponseTime() { return averageResponseTime; }
        public void setAverageResponseTime(double averageResponseTime) { this.averageResponseTime = averageResponseTime; }
        
        public Map<String, String> getHealthIssues() { return healthIssues; }
        public void addHealthIssue(String issue) { healthIssues.put("issue_" + System.currentTimeMillis(), issue); }
        
        public boolean isSystemHealthy() {
            return databaseHealthy && cacheHealthy && telegramHealthy && healthIssues.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("SystemHealth{users=%d, portfolios=%d, healthy=%s, successRate=%.1f%%, responseTime=%.0fms}", 
                               totalUsers, totalPortfolios, isSystemHealthy(), requestSuccessRate, averageResponseTime);
        }
    }
}