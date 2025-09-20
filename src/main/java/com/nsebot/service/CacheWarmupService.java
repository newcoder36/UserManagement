package com.nsebot.service;

import com.nsebot.dto.StockData;
import com.nsebot.service.impl.NSEDataServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cache Warmup Service
 * 
 * Responsible for:
 * - Pre-loading frequently accessed data into cache
 * - Scheduled cache refresh for critical data
 * - Performance optimization through predictive caching
 * - Cache statistics and monitoring
 */
@Service
public class CacheWarmupService {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupService.class);

    @Autowired
    private NSEDataService nseDataService;

    @Autowired
    private CacheManager cacheManager;

    // Top 20 most frequently accessed stocks for cache warming
    private static final List<String> TOP_STOCKS = List.of(
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "HINDUNILVR", 
        "ICICIBANK", "HDFC", "ITC", "KOTAKBANK", "LT",
        "AXISBANK", "BHARTIARTL", "ASIANPAINT", "MARUTI", "BAJFINANCE",
        "NESTLEIND", "HCLTECH", "WIPRO", "ULTRACEMCO", "SUNPHARMA"
    );

    /**
     * Initialize cache warming after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Starting cache warmup process...");
        warmupCriticalData();
    }

    /**
     * Scheduled cache refresh every 5 minutes during market hours
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledCacheRefresh() {
        if (isMarketHours()) {
            logger.debug("Starting scheduled cache refresh for market hours");
            refreshTopStocksData();
        }
    }

    /**
     * Scheduled cache refresh every 30 minutes during off-market hours
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void scheduledOffMarketCacheRefresh() {
        if (!isMarketHours()) {
            logger.debug("Starting scheduled cache refresh for off-market hours");
            refreshHistoricalData();
        }
    }

    /**
     * Warm up critical cache data
     */
    @Async
    public void warmupCriticalData() {
        try {
            // Warm up Nifty 100 symbols (rarely changes)
            CompletableFuture<Void> symbolsWarmup = CompletableFuture.runAsync(() -> {
                try {
                    nseDataService.getNifty100Symbols();
                    logger.debug("Warmed up Nifty 100 symbols cache");
                } catch (Exception e) {
                    logger.warn("Failed to warm up Nifty symbols cache", e);
                }
            });

            // Warm up top stocks current data
            CompletableFuture<Void> stockDataWarmup = CompletableFuture.runAsync(this::warmupTopStocks);

            // Warm up historical data for top 10 stocks
            CompletableFuture<Void> historicalWarmup = CompletableFuture.runAsync(this::warmupHistoricalData);

            // Wait for all warmup tasks to complete
            CompletableFuture.allOf(symbolsWarmup, stockDataWarmup, historicalWarmup)
                .thenRun(() -> logger.info("Cache warmup completed successfully"))
                .exceptionally(throwable -> {
                    logger.error("Cache warmup failed", throwable);
                    return null;
                });

        } catch (Exception e) {
            logger.error("Error during cache warmup", e);
        }
    }

    /**
     * Warm up top stocks current data
     */
    private void warmupTopStocks() {
        logger.debug("Warming up top stocks data...");
        
        List<CompletableFuture<Void>> futures = TOP_STOCKS.parallelStream()
            .map(symbol -> CompletableFuture.runAsync(() -> {
                try {
                    nseDataService.getStockData(symbol);
                    logger.trace("Warmed up stock data for {}", symbol);
                } catch (Exception e) {
                    logger.warn("Failed to warm up stock data for {}: {}", symbol, e.getMessage());
                }
            }))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> logger.debug("Completed warming up {} top stocks data", TOP_STOCKS.size()))
            .join(); // Wait for completion
    }

    /**
     * Warm up historical data for top stocks
     */
    private void warmupHistoricalData() {
        logger.debug("Warming up historical data for top stocks...");
        
        // Warm up historical data for top 10 stocks only (more resource intensive)
        List<String> topTenStocks = TOP_STOCKS.subList(0, 10);
        
        List<CompletableFuture<Void>> futures = topTenStocks.parallelStream()
            .map(symbol -> CompletableFuture.runAsync(() -> {
                try {
                    // Warm up different time periods
                    nseDataService.getHistoricalData(symbol, 30); // 30 days
                    nseDataService.getHistoricalData(symbol, 50); // 50 days
                    logger.trace("Warmed up historical data for {}", symbol);
                } catch (Exception e) {
                    logger.warn("Failed to warm up historical data for {}: {}", symbol, e.getMessage());
                }
            }))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> logger.debug("Completed warming up historical data for {} stocks", topTenStocks.size()))
            .join();
    }

    /**
     * Refresh top stocks data during market hours
     */
    @Async
    public void refreshTopStocksData() {
        logger.debug("Refreshing top stocks data cache...");
        
        // Clear existing cache entries first for fresh data
        TOP_STOCKS.parallelStream().forEach(symbol -> {
            try {
                clearStockDataCache(symbol);
                // Immediately reload to cache
                nseDataService.getStockData(symbol);
                logger.trace("Refreshed cache for {}", symbol);
            } catch (Exception e) {
                logger.warn("Failed to refresh cache for {}: {}", symbol, e.getMessage());
            }
        });
        
        logger.debug("Completed refreshing top stocks data cache");
    }

    /**
     * Refresh historical data during off-market hours
     */
    @Async
    public void refreshHistoricalData() {
        logger.debug("Refreshing historical data cache...");
        
        TOP_STOCKS.subList(0, 5).parallelStream().forEach(symbol -> {
            try {
                // Clear and reload historical data cache
                clearHistoricalDataCache(symbol);
                nseDataService.getHistoricalData(symbol, 30);
                nseDataService.getHistoricalData(symbol, 50);
                logger.trace("Refreshed historical cache for {}", symbol);
            } catch (Exception e) {
                logger.warn("Failed to refresh historical cache for {}: {}", symbol, e.getMessage());
            }
        });
        
        logger.debug("Completed refreshing historical data cache");
    }

    /**
     * Clear specific stock data from cache
     */
    public void clearStockDataCache(String symbol) {
        try {
            var cache = cacheManager.getCache("stockData");
            if (cache != null) {
                cache.evict(symbol.toUpperCase());
                logger.trace("Evicted stock data cache for {}", symbol);
            }
        } catch (Exception e) {
            logger.warn("Failed to evict stock data cache for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Clear historical data from cache
     */
    public void clearHistoricalDataCache(String symbol) {
        try {
            var cache = cacheManager.getCache("historicalData");
            if (cache != null) {
                // Clear different time periods
                cache.evict(symbol.toUpperCase() + "_30");
                cache.evict(symbol.toUpperCase() + "_50");
                logger.trace("Evicted historical data cache for {}", symbol);
            }
        } catch (Exception e) {
            logger.warn("Failed to evict historical data cache for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Clear all analysis caches for a symbol
     */
    public void clearAnalysisCaches(String symbol) {
        try {
            String upperSymbol = symbol.toUpperCase();
            
            // Clear various analysis caches
            clearCacheEntry("technicalAnalysis", upperSymbol);
            clearCacheEntry("mlPrediction", upperSymbol);
            clearCacheEntry("newsSentiment", upperSymbol);
            clearCacheEntry("stockAnalysis", upperSymbol);
            
            logger.debug("Cleared all analysis caches for {}", symbol);
        } catch (Exception e) {
            logger.warn("Failed to clear analysis caches for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Clear market-wide caches
     */
    public void clearMarketCaches() {
        try {
            clearCacheEntry("marketScan", "nifty100");
            clearCacheEntry("marketStatus", "status");
            logger.debug("Cleared market-wide caches");
        } catch (Exception e) {
            logger.warn("Failed to clear market caches", e);
        }
    }

    /**
     * Helper method to clear cache entry
     */
    private void clearCacheEntry(String cacheName, String key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    /**
     * Check if current time is during market hours
     * NSE: 9:15 AM to 3:30 PM IST, Monday to Friday
     */
    public boolean isMarketHours() {
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.DayOfWeek dayOfWeek = java.time.LocalDate.now().getDayOfWeek();
        
        // Check if it's a weekday
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return false;
        }
        
        // Check if time is between 9:15 AM and 3:30 PM
        java.time.LocalTime marketOpen = java.time.LocalTime.of(9, 15);
        java.time.LocalTime marketClose = java.time.LocalTime.of(15, 30);
        
        return now.isAfter(marketOpen) && now.isBefore(marketClose);
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Cache Statistics:\n");
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                stats.append(String.format("- %s: Active\n", cacheName));
            }
        });
        
        return stats.toString();
    }

    /**
     * Manual cache warming trigger (useful for testing)
     */
    public void triggerManualWarmup() {
        logger.info("Manual cache warmup triggered");
        warmupCriticalData();
    }
}