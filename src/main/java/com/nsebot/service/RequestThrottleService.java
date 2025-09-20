package com.nsebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smart request throttling service to avoid rate limits and bot detection
 */
@Service
public class RequestThrottleService {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestThrottleService.class);
    
    // Track last request times for different APIs
    private final ConcurrentHashMap<String, LocalDateTime> lastRequestTimes = new ConcurrentHashMap<>();
    
    // Rate limiting configurations
    private static final Duration NSE_MIN_DELAY = Duration.ofMillis(2000);  // 2 seconds minimum between NSE requests
    private static final Duration YAHOO_MIN_DELAY = Duration.ofMillis(1000); // 1 second minimum between Yahoo requests
    private static final Duration NSE_RANDOM_DELAY = Duration.ofMillis(3000); // Up to 3 additional seconds
    private static final Duration YAHOO_RANDOM_DELAY = Duration.ofMillis(2000); // Up to 2 additional seconds
    
    /**
     * Wait for appropriate delay before making NSE API request
     */
    public void waitForNseRequest(String symbol) {
        waitForRequest("NSE_" + symbol, NSE_MIN_DELAY, NSE_RANDOM_DELAY);
    }
    
    /**
     * Wait for appropriate delay before making Yahoo Finance request
     */
    public void waitForYahooRequest(String symbol) {
        waitForRequest("YAHOO_" + symbol, YAHOO_MIN_DELAY, YAHOO_RANDOM_DELAY);
    }
    
    /**
     * Generic wait mechanism with smart timing
     */
    private void waitForRequest(String key, Duration minDelay, Duration randomDelay) {
        LocalDateTime lastRequest = lastRequestTimes.get(key);
        LocalDateTime now = LocalDateTime.now();
        
        if (lastRequest != null) {
            Duration timeSinceLastRequest = Duration.between(lastRequest, now);
            Duration requiredDelay = minDelay.plus(
                Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, randomDelay.toMillis()))
            );
            
            if (timeSinceLastRequest.compareTo(requiredDelay) < 0) {
                Duration waitTime = requiredDelay.minus(timeSinceLastRequest);
                try {
                    logger.debug("Throttling request for {}: waiting {}ms", key, waitTime.toMillis());
                    Thread.sleep(waitTime.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Request throttling interrupted for {}", key);
                }
            }
        }
        
        lastRequestTimes.put(key, LocalDateTime.now());
    }
    
    /**
     * Add smart jitter to avoid predictable patterns
     */
    public void addRandomJitter() {
        try {
            int jitterMs = ThreadLocalRandom.current().nextInt(100, 500);
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Reset throttling for testing or emergency situations
     */
    public void resetThrottling() {
        lastRequestTimes.clear();
        logger.info("Request throttling reset");
    }
    
    /**
     * Get statistics on request patterns
     */
    public String getThrottleStats() {
        return String.format("Tracked API endpoints: %d", lastRequestTimes.size());
    }
}