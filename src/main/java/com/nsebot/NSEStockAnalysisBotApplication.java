package com.nsebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for NSE Stock Analysis Bot
 * 
 * Features:
 * - Telegram Bot integration for real-time stock analysis
 * - NSE API integration for market data
 * - ML-based stock recommendations
 * - Technical analysis and sentiment analysis
 */
@SpringBootApplication(scanBasePackages = "com.nsebot")
@EnableCaching
@EnableAsync
@EnableScheduling
public class NSEStockAnalysisBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NSEStockAnalysisBotApplication.class, args);
    }
}