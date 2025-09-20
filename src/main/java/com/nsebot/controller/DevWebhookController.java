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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Development webhook controller for testing Telegram bot integration
 */
@RestController
@Profile({"dev", "default"})
public class DevWebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(DevWebhookController.class);
    
    @Autowired
    private NSEStockAnalysisBot telegramBot;
    
    /**
     * Development webhook endpoint - accepts both GET and POST
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Update update) {
        logger.info("🔔 Received Telegram webhook update: {}", update.getUpdateId());
        
        try {
            // Process update asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    telegramBot.onUpdateReceived(update);
                } catch (Exception e) {
                    logger.error("❌ Error processing webhook update {}: {}", update.getUpdateId(), e.getMessage(), e);
                }
            });
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            logger.error("❌ Error handling webhook request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }
    
    /**
     * Simple GET endpoint for webhook testing
     */
    @GetMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhookInfo() {
        try {
            Map<String, Object> info = Map.of(
                "status", "active",
                "bot_username", telegramBot.getBotUsername(),
                "mode", "development",
                "timestamp", System.currentTimeMillis(),
                "message", "Development webhook endpoint is ready"
            );
            
            logger.info("📊 Webhook info requested: {}", info);
            return ResponseEntity.ok(info);
            
        } catch (Exception e) {
            logger.error("❌ Error getting webhook info: {}", e.getMessage(), e);
            Map<String, Object> error = Map.of(
                "status", "error",
                "message", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Test endpoint to send a message to a chat
     */
    @PostMapping("/webhook/test/{chatId}")
    public ResponseEntity<String> testMessage(@PathVariable String chatId, @RequestBody(required = false) String message) {
        try {
            String testMessage = message != null ? message : "🤖 Test message from NSE Bot! Your enhanced live data system is working.";
            
            // This would normally use telegramBot.sendMessage, but let's just log for now
            logger.info("📤 Test message to chat {}: {}", chatId, testMessage);
            
            return ResponseEntity.ok("Test message logged (bot integration pending)");
            
        } catch (Exception e) {
            logger.error("❌ Error sending test message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR: " + e.getMessage());
        }
    }
}