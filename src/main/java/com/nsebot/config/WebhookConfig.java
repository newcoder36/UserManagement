package com.nsebot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Webhook configuration for production deployment
 * Switches between polling (development) and webhook (production) modes
 */
@Configuration
public class WebhookConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookConfig.class);
    
    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;
    
    @Value("${telegram.bot.webhook-path:/webhook}")
    private String webhookPath;
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${telegram.bot.max-connections:100}")
    private int maxConnections;
    
    @Value("${telegram.bot.allowed-updates:}")
    private String allowedUpdates;
    
    /**
     * Create webhook configuration for production
     */
    @Bean
    @Profile("prod")
    public SetWebhook setWebhookInstance() {
        logger.info("Configuring Telegram webhook for production deployment");
        
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL must be configured for production profile");
        }
        
        String fullWebhookUrl = webhookUrl + webhookPath;
        
        SetWebhook setWebhook = SetWebhook.builder()
            .url(fullWebhookUrl)
            .maxConnections(maxConnections)
            .build();
        
        // Set allowed updates if configured
        if (allowedUpdates != null && !allowedUpdates.trim().isEmpty()) {
            String[] updates = allowedUpdates.split(",");
            for (String update : updates) {
                setWebhook.getAllowedUpdates().add(update.trim());
            }
        }
        
        logger.info("Webhook configured: URL={}, maxConnections={}, allowedUpdates={}", 
                   fullWebhookUrl, maxConnections, allowedUpdates);
        
        return setWebhook;
    }
    
    /**
     * Webhook deployment information
     */
    @Bean
    public WebhookInfo webhookInfo() {
        return new WebhookInfo(
            webhookUrl + webhookPath,
            maxConnections,
            allowedUpdates != null ? allowedUpdates.split(",") : new String[0]
        );
    }
    
    /**
     * Webhook information data class
     */
    public static class WebhookInfo {
        private final String url;
        private final int maxConnections;
        private final String[] allowedUpdates;
        
        public WebhookInfo(String url, int maxConnections, String[] allowedUpdates) {
            this.url = url;
            this.maxConnections = maxConnections;
            this.allowedUpdates = allowedUpdates;
        }
        
        public String getUrl() { return url; }
        public int getMaxConnections() { return maxConnections; }
        public String[] getAllowedUpdates() { return allowedUpdates; }
        
        public boolean isWebhookEnabled() {
            return url != null && !url.trim().isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("WebhookInfo{url='%s', maxConnections=%d, allowedUpdates=%d}", 
                               url, maxConnections, allowedUpdates.length);
        }
    }
}