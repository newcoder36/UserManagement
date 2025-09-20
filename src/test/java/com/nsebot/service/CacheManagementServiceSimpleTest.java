package com.nsebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class CacheManagementServiceSimpleTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private Cache mockCache;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private RedisServerCommands serverCommands;

    @InjectMocks
    private CacheManagementService cacheManagementService;

    @BeforeEach
    void setUp() {
        when(cacheManager.getCacheNames()).thenReturn(Set.of("stockData", "historicalData", "technicalAnalysis"));
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);
    }

    @Test
    void testGetCacheStatistics() {
        Map<String, Object> stats = cacheManagementService.getCacheStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalCaches"));
        assertTrue(stats.containsKey("cacheNames"));
        assertTrue(stats.containsKey("cacheDetails"));
    }

    @Test
    void testGetCacheDetails() {
        Map<String, Object> details = cacheManagementService.getCacheDetails("stockData");
        
        assertNotNull(details);
        assertTrue(details.containsKey("status"));
        assertEquals("ACTIVE", details.get("status"));
    }

    @Test
    void testGetCacheDetailsNotFound() {
        when(cacheManager.getCache("nonexistent")).thenReturn(null);
        
        Map<String, Object> details = cacheManagementService.getCacheDetails("nonexistent");
        
        assertNotNull(details);
        assertEquals("NOT_FOUND", details.get("status"));
    }

    @Test
    void testGetCacheHealth() {
        Map<String, Object> health = cacheManagementService.getCacheHealth();
        
        assertNotNull(health);
        assertTrue(health.containsKey("overallHealth"));
        assertTrue(health.containsKey("activeCaches"));
    }

    @Test
    void testClearAllCaches() {
        assertDoesNotThrow(() -> cacheManagementService.clearAllCaches());
        
        verify(mockCache, times(3)).clear();
    }

    @Test
    void testClearCache() {
        boolean result = cacheManagementService.clearCache("stockData");
        
        assertTrue(result);
        verify(mockCache).clear();
    }

    @Test
    void testClearCacheNotFound() {
        when(cacheManager.getCache("nonexistent")).thenReturn(null);
        
        boolean result = cacheManagementService.clearCache("nonexistent");
        
        assertFalse(result);
    }

    @Test
    void testClearCacheEntry() {
        boolean result = cacheManagementService.clearCacheEntry("stockData", "RELIANCE");
        
        assertTrue(result);
        verify(mockCache).evict("RELIANCE");
    }

    @Test
    void testGetOptimizationRecommendations() {
        Map<String, Object> recommendations = cacheManagementService.getOptimizationRecommendations();
        
        assertNotNull(recommendations);
    }

    @Test
    void testPerformMaintenance() {
        assertDoesNotThrow(() -> cacheManagementService.performMaintenance());
    }
}