package com.nsebot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Telegram Bot
 */
@Configuration
public class TelegramBotConfig {
    
    @Value("${telegram.bot.token:8380622246:AAHHkjfnuK7z2KVFc11Bnat0uVtweNuSLHA}")
    private String token;
    
    @Value("${telegram.bot.username:missiontrade_bot}")
    private String username;
    
    @Value("${telegram.bot.webhook-path:/webhook}")
    private String webhookPath;
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getWebhookPath() {
        return webhookPath;
    }
    
    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }
}