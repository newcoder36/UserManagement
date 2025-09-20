package com.nsebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Yahoo Finance API
 */
@Configuration
@ConfigurationProperties(prefix = "yahoo.finance")
public class YahooFinanceConfig {
    
    private int timeout = 8000; // 8 seconds
    private int retryAttempts = 2;
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private RateLimit rateLimit = new RateLimit();
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getRetryAttempts() {
        return retryAttempts;
    }
    
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
    
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
    
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }
    
    public RateLimit getRateLimit() {
        return rateLimit;
    }
    
    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }
    
    public static class CircuitBreaker {
        private int failureThreshold = 5;
        private int timeout = 30000; // 30 seconds
        
        public int getFailureThreshold() {
            return failureThreshold;
        }
        
        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }
    
    public static class RateLimit {
        private int requestsPerMinute = 120;
        
        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }
        
        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}