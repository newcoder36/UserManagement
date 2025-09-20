package com.nsebot.controller;

import com.nsebot.service.CacheManagementService;
import com.nsebot.service.CacheWarmupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Cache Monitoring and Management
 * 
 * Provides endpoints for:
 * - Cache statistics and monitoring
 * - Manual cache operations
 * - Cache health checks
 * - Performance optimization
 */
@RestController
@RequestMapping("/api/cache")
public class CacheMonitoringController {

    @Autowired
    private CacheManagementService cacheManagementService;

    @Autowired
    private CacheWarmupService cacheWarmupService;

    /**
     * Get comprehensive cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            Map<String, Object> stats = cacheManagementService.getCacheStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve cache statistics");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get cache health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getCacheHealth() {
        try {
            Map<String, Object> health = cacheManagementService.getCacheHealth();
            
            // Return appropriate HTTP status based on health
            String overallHealth = (String) health.get("overallHealth");
            if ("HEALTHY".equals(overallHealth)) {
                return ResponseEntity.ok(health);
            } else {
                return ResponseEntity.status(503).body(health);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("overallHealth", "UNHEALTHY");
            error.put("error", "Failed to check cache health");
            error.put("message", e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

    /**
     * Get specific cache details
     */
    @GetMapping("/details/{cacheName}")
    public ResponseEntity<Map<String, Object>> getCacheDetails(@PathVariable String cacheName) {
        try {
            Map<String, Object> details = cacheManagementService.getCacheDetails(cacheName);
            
            if ("NOT_FOUND".equals(details.get("status"))) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve cache details");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear all caches
     */
    @PostMapping("/clear/all")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        try {
            cacheManagementService.clearAllCaches();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All caches cleared successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to clear all caches");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear specific cache
     */
    @PostMapping("/clear/{cacheName}")
    public ResponseEntity<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        try {
            boolean success = cacheManagementService.clearCache(cacheName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            
            if (success) {
                response.put("message", "Cache '" + cacheName + "' cleared successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Cache '" + cacheName + "' not found");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to clear cache");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear specific cache entry
     */
    @PostMapping("/clear/{cacheName}/{key}")
    public ResponseEntity<Map<String, Object>> clearCacheEntry(
            @PathVariable String cacheName, 
            @PathVariable String key) {
        try {
            boolean success = cacheManagementService.clearCacheEntry(cacheName, key);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            
            if (success) {
                response.put("message", "Cache entry cleared successfully");
            } else {
                response.put("message", "Cache or entry not found");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to clear cache entry");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Trigger manual cache warmup
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> triggerCacheWarmup() {
        try {
            cacheWarmupService.triggerManualWarmup();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache warmup initiated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to initiate cache warmup");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear analysis caches for specific symbol
     */
    @PostMapping("/clear/analysis/{symbol}")
    public ResponseEntity<Map<String, Object>> clearAnalysisCaches(@PathVariable String symbol) {
        try {
            cacheWarmupService.clearAnalysisCaches(symbol.toUpperCase());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Analysis caches cleared for " + symbol.toUpperCase());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to clear analysis caches");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear market-wide caches
     */
    @PostMapping("/clear/market")
    public ResponseEntity<Map<String, Object>> clearMarketCaches() {
        try {
            cacheWarmupService.clearMarketCaches();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Market caches cleared successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to clear market caches");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get cache optimization recommendations
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getOptimizationRecommendations() {
        try {
            Map<String, Object> recommendations = cacheManagementService.getOptimizationRecommendations();
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate recommendations");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Perform cache maintenance
     */
    @PostMapping("/maintenance")
    public ResponseEntity<Map<String, Object>> performCacheMaintenance() {
        try {
            cacheManagementService.performMaintenance();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache maintenance completed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to perform cache maintenance");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get cache warmup statistics
     */
    @GetMapping("/warmup/stats")
    public ResponseEntity<Map<String, Object>> getCacheWarmupStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("message", cacheWarmupService.getCacheStatistics());
            stats.put("marketHours", cacheWarmupService.isMarketHours());
            stats.put("lastWarmup", "Available in logs");
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve warmup statistics");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}