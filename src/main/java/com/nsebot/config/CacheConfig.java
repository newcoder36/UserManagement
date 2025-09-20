package com.nsebot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration for NSE Stock Analysis Bot
 * 
 * Configures different cache strategies for different data types:
 * - Real-time stock data: Short TTL (1-2 minutes)
 * - Historical data: Medium TTL (15-30 minutes)
 * - Analysis results: Medium TTL (5-10 minutes)
 * - Market status: Short TTL (1 minute)
 * - News sentiment: Long TTL (1-2 hours)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Custom cache manager with specific TTL configurations
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Configure Jackson ObjectMapper for JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10)) // Default 10 minutes TTL
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(jsonRedisSerializer));

        // Cache-specific configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Real-time stock data - Short TTL for fresh market data
        cacheConfigurations.put("stockData", defaultConfig
            .entryTtl(Duration.ofMinutes(2))
            .prefixCacheNameWith("stock:"));
            
        // Historical data - Medium TTL as it doesn't change frequently
        cacheConfigurations.put("historicalData", defaultConfig
            .entryTtl(Duration.ofMinutes(30))
            .prefixCacheNameWith("historical:"));
            
        // Technical analysis results - Short-medium TTL
        cacheConfigurations.put("technicalAnalysis", defaultConfig
            .entryTtl(Duration.ofMinutes(5))
            .prefixCacheNameWith("technical:"));
            
        // ML predictions - Medium TTL
        cacheConfigurations.put("mlPrediction", defaultConfig
            .entryTtl(Duration.ofMinutes(10))
            .prefixCacheNameWith("ml:"));
            
        // News sentiment - Longer TTL as news doesn't change frequently
        cacheConfigurations.put("newsSentiment", defaultConfig
            .entryTtl(Duration.ofHours(1))
            .prefixCacheNameWith("news:"));
            
        // Market status - Very short TTL for real-time status
        cacheConfigurations.put("marketStatus", defaultConfig
            .entryTtl(Duration.ofMinutes(1))
            .prefixCacheNameWith("market:"));
            
        // Nifty 100 symbols - Long TTL as this rarely changes
        cacheConfigurations.put("nifty100Symbols", defaultConfig
            .entryTtl(Duration.ofHours(12))
            .prefixCacheNameWith("symbols:"));
            
        // Market scan results - Short TTL for fresh recommendations
        cacheConfigurations.put("marketScan", defaultConfig
            .entryTtl(Duration.ofMinutes(3))
            .prefixCacheNameWith("scan:"));
            
        // Individual stock analysis - Short-medium TTL
        cacheConfigurations.put("stockAnalysis", defaultConfig
            .entryTtl(Duration.ofMinutes(8))
            .prefixCacheNameWith("analysis:"));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware() // Enable transaction support
            .build();
    }
}