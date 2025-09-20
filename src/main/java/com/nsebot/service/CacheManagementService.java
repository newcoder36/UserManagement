package com.nsebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cache Management Service
 * 
 * Provides advanced cache management capabilities:
 * - Cache performance monitoring
 * - Memory usage tracking
 * - Cache hit/miss statistics
 * - Intelligent cache eviction
 * - Cache health checks
 */
@Service
public class CacheManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CacheManagementService.class);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Get comprehensive cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // Get all cache names
            var cacheNamesCollection = cacheManager.getCacheNames();
            Set<String> cacheNames = Set.copyOf(cacheNamesCollection);
            statistics.put("totalCaches", cacheNames.size());
            statistics.put("cacheNames", cacheNames);

            // Get statistics for each cache
            Map<String, Map<String, Object>> cacheDetails = new HashMap<>();
            
            for (String cacheName : cacheNames) {
                Map<String, Object> cacheStats = getCacheDetails(cacheName);
                cacheDetails.put(cacheName, cacheStats);
            }
            
            statistics.put("cacheDetails", cacheDetails);
            
            // Get Redis-specific statistics
            statistics.put("redisInfo", getRedisInfo());
            
        } catch (Exception e) {
            logger.error("Error gathering cache statistics", e);
            statistics.put("error", "Failed to gather statistics: " + e.getMessage());
        }
        
        return statistics;
    }

    /**
     * Get detailed information about a specific cache
     */
    public Map<String, Object> getCacheDetails(String cacheName) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                details.put("status", "NOT_FOUND");
                return details;
            }
            
            details.put("status", "ACTIVE");
            details.put("cacheType", cache.getClass().getSimpleName());
            
            if (cache instanceof RedisCache redisCache) {
                // Get Redis-specific cache information
                String cachePrefix = getCachePrefix(cacheName);
                details.put("prefix", cachePrefix);
                details.put("keyCount", getKeyCount(cachePrefix));
                details.put("memoryUsage", getMemoryUsage(cachePrefix));
                details.put("ttlInfo", getTTLInfo(cacheName));
            }
            
        } catch (Exception e) {
            logger.error("Error getting cache details for {}", cacheName, e);
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Get cache health status
     */
    public Map<String, Object> getCacheHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test basic Redis functionality by attempting to set and get a test value
            redisTemplate.opsForValue().set("health-check", "test", 5, java.util.concurrent.TimeUnit.SECONDS);
            String testResult = (String) redisTemplate.opsForValue().get("health-check");
            
            boolean isHealthy = "test".equals(testResult);
            
            health.put("redisConnectivity", isHealthy ? "HEALTHY" : "UNHEALTHY");
            health.put("testResult", testResult);
            
            // Check cache manager status
            int activeCaches = cacheManager.getCacheNames().size();
            health.put("activeCaches", activeCaches);
            health.put("cacheManager", "HEALTHY");
            
            // Overall health based on successful Redis operation
            health.put("overallHealth", isHealthy ? "HEALTHY" : "UNHEALTHY");
            
            logger.info("Cache health check completed. Healthy: {}", isHealthy);
            
        } catch (Exception e) {
            logger.error("Error checking cache health", e);
            health.put("overallHealth", "UNHEALTHY");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        try {
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    logger.info("Cleared cache: {}", cacheName);
                }
            }
            logger.info("All caches cleared successfully");
        } catch (Exception e) {
            logger.error("Error clearing all caches", e);
            throw new RuntimeException("Failed to clear all caches", e);
        }
    }

    /**
     * Clear specific cache
     */
    public boolean clearCache(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("Cleared cache: {}", cacheName);
                return true;
            } else {
                logger.warn("Cache not found: {}", cacheName);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error clearing cache {}", cacheName, e);
            return false;
        }
    }

    /**
     * Clear cache entry by key
     */
    public boolean clearCacheEntry(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                logger.debug("Evicted key {} from cache {}", key, cacheName);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error evicting key {} from cache {}", key, cacheName, e);
            return false;
        }
    }

    /**
     * Get memory usage optimization recommendations
     */
    public Map<String, Object> getOptimizationRecommendations() {
        Map<String, Object> recommendations = new HashMap<>();
        
        try {
            Map<String, Object> stats = getCacheStatistics();
            
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> cacheDetails = 
                (Map<String, Map<String, Object>>) stats.get("cacheDetails");
            
            // Analyze each cache for optimization opportunities
            for (Map.Entry<String, Map<String, Object>> entry : cacheDetails.entrySet()) {
                String cacheName = entry.getKey();
                Map<String, Object> details = entry.getValue();
                
                analyzeCache(cacheName, details, recommendations);
            }
            
        } catch (Exception e) {
            logger.error("Error generating optimization recommendations", e);
            recommendations.put("error", "Failed to generate recommendations: " + e.getMessage());
        }
        
        return recommendations;
    }

    /**
     * Perform cache maintenance
     */
    public void performMaintenance() {
        try {
            logger.info("Starting cache maintenance...");
            
            // Get recommendations
            Map<String, Object> recommendations = getOptimizationRecommendations();
            
            // Apply automatic optimizations
            applyAutomaticOptimizations(recommendations);
            
            // Log maintenance completion
            logger.info("Cache maintenance completed");
            
        } catch (Exception e) {
            logger.error("Error during cache maintenance", e);
        }
    }

    /**
     * Apply automatic cache optimizations
     */
    private void applyAutomaticOptimizations(Map<String, Object> recommendations) {
        // Implementation for automatic optimizations
        // For now, just log the recommendations
        logger.info("Cache optimization recommendations: {}", recommendations);
    }

    /**
     * Analyze individual cache for optimization
     */
    private void analyzeCache(String cacheName, Map<String, Object> details, 
                            Map<String, Object> recommendations) {
        
        try {
            Object keyCountObj = details.get("keyCount");
            if (keyCountObj instanceof Number keyCount) {
                int count = keyCount.intValue();
                
                // Check for caches with too many keys
                if (count > 10000) {
                    recommendations.put(cacheName + "_highKeyCount", 
                        "Cache has " + count + " keys. Consider shorter TTL or selective eviction.");
                }
                
                // Check for empty caches
                if (count == 0) {
                    recommendations.put(cacheName + "_empty", 
                        "Cache is empty. Consider cache warming or reviewing usage patterns.");
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error analyzing cache {}: {}", cacheName, e.getMessage());
        }
    }

    /**
     * Helper methods for Redis-specific operations
     */
    private String getCachePrefix(String cacheName) {
        // Return cache prefix based on configuration
        return switch (cacheName) {
            case "stockData" -> "stock:";
            case "historicalData" -> "historical:";
            case "technicalAnalysis" -> "technical:";
            case "mlPrediction" -> "ml:";
            case "newsSentiment" -> "news:";
            case "marketStatus" -> "market:";
            case "nifty100Symbols" -> "symbols:";
            case "marketScan" -> "scan:";
            case "stockAnalysis" -> "analysis:";
            default -> cacheName + ":";
        };
    }

    private long getKeyCount(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            logger.warn("Error getting key count for prefix {}: {}", prefix, e.getMessage());
            return -1;
        }
    }

    private String getMemoryUsage(String prefix) {
        try {
            // This is a simplified approach - in production, you'd use Redis MEMORY commands
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                return keys.size() + " keys";
            }
            return "0 keys";
        } catch (Exception e) {
            logger.warn("Error getting memory usage for prefix {}: {}", prefix, e.getMessage());
            return "unknown";
        }
    }

    private Map<String, Object> getTTLInfo(String cacheName) {
        Map<String, Object> ttlInfo = new HashMap<>();
        
        // Return configured TTL information
        ttlInfo.put("configured", getCacheConfiguredTTL(cacheName));
        
        return ttlInfo;
    }

    private String getCacheConfiguredTTL(String cacheName) {
        return switch (cacheName) {
            case "stockData" -> "2 minutes";
            case "historicalData" -> "30 minutes";
            case "technicalAnalysis" -> "5 minutes";
            case "mlPrediction" -> "10 minutes";
            case "newsSentiment" -> "1 hour";
            case "marketStatus" -> "1 minute";
            case "nifty100Symbols" -> "12 hours";
            case "marketScan" -> "3 minutes";
            case "stockAnalysis" -> "8 minutes";
            default -> "10 minutes";
        };
    }

    private Map<String, Object> getRedisInfo() {
        Map<String, Object> redisInfo = new HashMap<>();
        
        try {
            // Get basic Redis information
            redisInfo.put("connectionActive", redisTemplate.getConnectionFactory() != null);
            redisInfo.put("databaseSize", redisTemplate.getConnectionFactory()
                .getConnection().commands().dbSize());
            
        } catch (Exception e) {
            logger.warn("Error getting Redis info: {}", e.getMessage());
            redisInfo.put("error", e.getMessage());
        }
        
        return redisInfo;
    }
}