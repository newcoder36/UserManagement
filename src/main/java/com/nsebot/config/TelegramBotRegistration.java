package com.nsebot.config;

import com.nsebot.service.NSEStockAnalysisBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Configuration to register the Telegram bot with the TelegramBotsApi
 */
@Component
public class TelegramBotRegistration implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotRegistration.class);
    
    @Autowired
    private NSEStockAnalysisBot telegramBot;
    
    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("🤖 Initializing Telegram Bot API...");
            logger.info("🔧 Bot config - Username: '{}', Token: '{}...'", 
                       telegramBot.getBotUsername(), 
                       telegramBot.getBotToken().substring(0, 10));
            
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            
            logger.info("📝 Registering bot: {}", telegramBot.getBotUsername());
            botsApi.registerBot(telegramBot);
            
            logger.info("✅ Telegram bot '{}' successfully registered and ready to receive messages!", 
                       telegramBot.getBotUsername());
            logger.info("💬 Bot is now listening for messages in long polling mode");
            
        } catch (TelegramApiException e) {
            logger.error("❌ Failed to register Telegram bot: {}", e.getMessage());
            logger.error("🔍 Check your bot token and network connectivity");
            
            // Don't fail the application startup, just log the error
            logger.warn("⚠️ Application will continue without Telegram bot functionality");
        } catch (Exception e) {
            logger.error("❌ Unexpected error during bot registration: {}", e.getMessage(), e);
            logger.warn("⚠️ Application will continue without Telegram bot functionality");
        }
    }
}