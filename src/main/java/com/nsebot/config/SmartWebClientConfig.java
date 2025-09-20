package com.nsebot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Smart WebClient configuration with anti-bot evasion and rate limiting
 */
@Configuration
public class SmartWebClientConfig {

    private static final Random random = new Random();
    
    // Realistic browser User-Agent strings
    private static final List<String> USER_AGENTS = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    );
    
    // Realistic browser Accept headers
    private static final List<String> ACCEPT_HEADERS = Arrays.asList(
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "application/json,text/plain,*/*",
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    );
    
    public static String getRandomUserAgent() {
        return USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
    }
    
    public static String getRandomAcceptHeader() {
        return ACCEPT_HEADERS.get(random.nextInt(ACCEPT_HEADERS.size()));
    }
    
    @Bean("smartNseWebClient")
    public WebClient smartNseWebClient() {
        return WebClient.builder()
            .baseUrl("https://www.nseindia.com")
            .defaultHeader("User-Agent", getRandomUserAgent())
            .defaultHeader("Accept", getRandomAcceptHeader())
            .defaultHeader("Accept-Language", "en-US,en;q=0.9")
            .defaultHeader("Accept-Encoding", "gzip, deflate, br")
            .defaultHeader("DNT", "1")
            .defaultHeader("Connection", "keep-alive")
            .defaultHeader("Upgrade-Insecure-Requests", "1")
            .defaultHeader("Sec-Fetch-Dest", "document")
            .defaultHeader("Sec-Fetch-Mode", "navigate")
            .defaultHeader("Sec-Fetch-Site", "none")
            .defaultHeader("Cache-Control", "max-age=0")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }
    
    @Bean("smartYahooWebClient")
    public WebClient smartYahooWebClient() {
        return WebClient.builder()
            .baseUrl("https://query1.finance.yahoo.com/v8/finance/chart")
            .defaultHeader("User-Agent", getRandomUserAgent())
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Accept-Language", "en-US,en;q=0.9")
            .defaultHeader("Origin", "https://finance.yahoo.com")
            .defaultHeader("Referer", "https://finance.yahoo.com/")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }
}