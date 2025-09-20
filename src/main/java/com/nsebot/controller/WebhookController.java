package com.nsebot.controller;

import com.nsebot.service.NSEStockAnalysisBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for handling Telegram webhook requests in production
 */
@RestController
@Profile("prod")
public class WebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    @Autowired
    private NSEStockAnalysisBot telegramBot;
    
    /**
     * Handle incoming webhook requests from Telegram
     */
    @PostMapping(value = "${telegram.bot.webhook-path:/webhook}")
    public ResponseEntity<BotApiMethod<?>> onUpdateReceived(@RequestBody Update update) {
        logger.debug("Received webhook update: {}", update.getUpdateId());
        
        try {
            // Process update asynchronously for better performance
            CompletableFuture.runAsync(() -> {
                try {
                    telegramBot.onUpdateReceived(update);
                } catch (Exception e) {
                    logger.error("Error processing webhook update {}: {}", update.getUpdateId(), e.getMessage());
                }
            });
            
            // Return 200 OK immediately to acknowledge receipt
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            logger.error("Error handling webhook request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Health check endpoint for webhook
     */
    @GetMapping(value = "${telegram.bot.webhook-path:/webhook}/health")
    public ResponseEntity<WebhookHealthResponse> webhookHealth() {
        try {
            WebhookHealthResponse response = new WebhookHealthResponse(
                "healthy",
                System.currentTimeMillis(),
                telegramBot.getBotUsername(),
                "Webhook endpoint is operational"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Webhook health check failed: {}", e.getMessage());
            
            WebhookHealthResponse response = new WebhookHealthResponse(
                "unhealthy",
                System.currentTimeMillis(),
                "unknown",
                "Webhook endpoint error: " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
    
    /**
     * Get webhook statistics and information
     */
    @GetMapping(value = "${telegram.bot.webhook-path:/webhook}/info")
    public ResponseEntity<WebhookInfoResponse> webhookInfo() {
        try {
            WebhookInfoResponse response = new WebhookInfoResponse(
                telegramBot.getBotUsername(),
                telegramBot.getBotToken().substring(0, 10) + "...", // Masked token
                "webhook",
                true,
                System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting webhook info: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Webhook health response
     */
    public static class WebhookHealthResponse {
        private final String status;
        private final long timestamp;
        private final String botUsername;
        private final String message;
        
        public WebhookHealthResponse(String status, long timestamp, String botUsername, String message) {
            this.status = status;
            this.timestamp = timestamp;
            this.botUsername = botUsername;
            this.message = message;
        }
        
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
        public String getBotUsername() { return botUsername; }
        public String getMessage() { return message; }
    }
    
    /**
     * Webhook info response
     */
    public static class WebhookInfoResponse {
        private final String botUsername;
        private final String botToken;
        private final String mode;
        private final boolean active;
        private final long timestamp;
        
        public WebhookInfoResponse(String botUsername, String botToken, String mode, boolean active, long timestamp) {
            this.botUsername = botUsername;
            this.botToken = botToken;
            this.mode = mode;
            this.active = active;
            this.timestamp = timestamp;
        }
        
        public String getBotUsername() { return botUsername; }
        public String getBotToken() { return botToken; }
        public String getMode() { return mode; }
        public boolean isActive() { return active; }
        public long getTimestamp() { return timestamp; }
    }
}