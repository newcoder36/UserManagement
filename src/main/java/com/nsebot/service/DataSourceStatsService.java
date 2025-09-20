package com.nsebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Service to track and report statistics on data sources
 */
@Service
public class DataSourceStatsService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceStatsService.class);
    
    // Counters for each data source
    private final AtomicLong nseSuccessCount = new AtomicLong(0);
    private final AtomicLong yahooSuccessCount = new AtomicLong(0);
    private final AtomicLong mockDataCount = new AtomicLong(0);
    private final AtomicLong nseFailureCount = new AtomicLong(0);
    private final AtomicLong yahooFailureCount = new AtomicLong(0);
    
    // Track specific symbols per source
    private final Map<String, String> symbolToSource = new ConcurrentHashMap<>();
    private final LocalDateTime startTime = LocalDateTime.now();
    
    public enum DataSource {
        NSE("NSE API"),
        YAHOO("Yahoo Finance"),
        MOCK("Mock Data");
        
        private final String displayName;
        
        DataSource(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Record successful data fetch from NSE
     */
    public void recordNseSuccess(String symbol) {
        nseSuccessCount.incrementAndGet();
        symbolToSource.put(symbol, DataSource.NSE.displayName);
        logger.info("✅ NSE SUCCESS: {} (Total NSE: {})", symbol, nseSuccessCount.get());
    }
    
    /**
     * Record successful data fetch from Yahoo Finance
     */
    public void recordYahooSuccess(String symbol) {
        yahooSuccessCount.incrementAndGet();
        symbolToSource.put(symbol, DataSource.YAHOO.displayName);
        logger.info("✅ YAHOO SUCCESS: {} (Total Yahoo: {})", symbol, yahooSuccessCount.get());
    }
    
    /**
     * Record fallback to mock data
     */
    public void recordMockData(String symbol) {
        mockDataCount.incrementAndGet();
        symbolToSource.put(symbol, DataSource.MOCK.displayName);
        logger.info("⚠️ MOCK FALLBACK: {} (Total Mock: {})", symbol, mockDataCount.get());
    }
    
    /**
     * Record NSE API failure
     */
    public void recordNseFailure(String symbol, String reason) {
        nseFailureCount.incrementAndGet();
        logger.warn("❌ NSE FAILED: {} - {} (Total NSE Failures: {})", symbol, reason, nseFailureCount.get());
    }
    
    /**
     * Record Yahoo Finance failure
     */
    public void recordYahooFailure(String symbol, String reason) {
        yahooFailureCount.incrementAndGet();
        logger.warn("❌ YAHOO FAILED: {} - {} (Total Yahoo Failures: {})", symbol, reason, yahooFailureCount.get());
    }
    
    /**
     * Get comprehensive statistics
     */
    public DataSourceStatistics getStatistics() {
        return new DataSourceStatistics(
            nseSuccessCount.get(),
            yahooSuccessCount.get(),
            mockDataCount.get(),
            nseFailureCount.get(),
            yahooFailureCount.get(),
            symbolToSource,
            startTime
        );
    }
    
    /**
     * Reset all statistics
     */
    public void resetStatistics() {
        nseSuccessCount.set(0);
        yahooSuccessCount.set(0);
        mockDataCount.set(0);
        nseFailureCount.set(0);
        yahooFailureCount.set(0);
        symbolToSource.clear();
        logger.info("📊 Data source statistics reset");
    }
    
    /**
     * Data class to hold statistics
     */
    public static class DataSourceStatistics {
        private final long nseSuccessCount;
        private final long yahooSuccessCount;
        private final long mockDataCount;
        private final long nseFailureCount;
        private final long yahooFailureCount;
        private final Map<String, String> symbolToSource;
        private final LocalDateTime startTime;
        
        public DataSourceStatistics(long nseSuccessCount, long yahooSuccessCount, long mockDataCount,
                                  long nseFailureCount, long yahooFailureCount,
                                  Map<String, String> symbolToSource, LocalDateTime startTime) {
            this.nseSuccessCount = nseSuccessCount;
            this.yahooSuccessCount = yahooSuccessCount;
            this.mockDataCount = mockDataCount;
            this.nseFailureCount = nseFailureCount;
            this.yahooFailureCount = yahooFailureCount;
            this.symbolToSource = new ConcurrentHashMap<>(symbolToSource);
            this.startTime = startTime;
        }
        
        // Getters
        public long getNseSuccessCount() { return nseSuccessCount; }
        public long getYahooSuccessCount() { return yahooSuccessCount; }
        public long getMockDataCount() { return mockDataCount; }
        public long getNseFailureCount() { return nseFailureCount; }
        public long getYahooFailureCount() { return yahooFailureCount; }
        public Map<String, String> getSymbolToSource() { return symbolToSource; }
        public LocalDateTime getStartTime() { return startTime; }
        
        public long getTotalSuccessCount() {
            return nseSuccessCount + yahooSuccessCount + mockDataCount;
        }
        
        public long getTotalFailureCount() {
            return nseFailureCount + yahooFailureCount;
        }
        
        public double getNseSuccessRate() {
            long totalNse = nseSuccessCount + nseFailureCount;
            return totalNse > 0 ? (double) nseSuccessCount / totalNse * 100 : 0.0;
        }
        
        public double getYahooSuccessRate() {
            long totalYahoo = yahooSuccessCount + yahooFailureCount;
            return totalYahoo > 0 ? (double) yahooSuccessCount / totalYahoo * 100 : 0.0;
        }
    }
}