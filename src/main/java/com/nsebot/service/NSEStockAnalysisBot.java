package com.nsebot.service;

import com.nsebot.config.TelegramBotConfig;
import com.nsebot.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

/**
 * Enhanced Telegram Bot Service for NSE Stock Analysis
 * Integrates with user management, rate limiting, and portfolio tracking
 */
@Service
public class NSEStockAnalysisBot extends TelegramLongPollingBot {
    
    private static final Logger logger = LoggerFactory.getLogger(NSEStockAnalysisBot.class);
    
    private final TelegramBotConfig botConfig;
    private final StockAnalysisService analysisService;
    private final UserService userService;
    private final PortfolioService portfolioService;
    private final MonitoringService monitoringService;
    private final SimpleScanService simpleScanService;
    
    @Autowired
    public NSEStockAnalysisBot(TelegramBotConfig botConfig, 
                              StockAnalysisService analysisService,
                              UserService userService,
                              PortfolioService portfolioService,
                              MonitoringService monitoringService,
                              SimpleScanService simpleScanService) {
        this.botConfig = botConfig;
        this.analysisService = analysisService;
        this.userService = userService;
        this.portfolioService = portfolioService;
        this.monitoringService = monitoringService;
        this.simpleScanService = simpleScanService;
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }
    
    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                Long telegramId = message.getFrom().getId();
                String chatId = message.getChatId().toString();
                String messageText = message.getText();
                
                // Register or update user
                User user = userService.registerOrUpdateUser(update);
                if (user == null) {
                    logger.warn("Failed to register/update user for Telegram ID: {}", telegramId);
                    sendMessage(chatId, "❌ Unable to process your request. Please try again.");
                    return;
                }
                
                logger.info("Processing message from user {} ({}): {}", 
                           user.getDisplayName(), telegramId, messageText);
                
                // Check if user can make requests (rate limiting)
                if (!userService.canUserMakeRequest(telegramId)) {
                    User.SubscriptionTier tier = user.getSubscriptionTier();
                    String rateLimitMessage = String.format(
                        "⏱️ You've reached your daily limit of %d requests for %s tier.\n" +
                        "Upgrade to Premium (100/day) or Pro (500/day) for more requests!\n\n" +
                        "Use /upgrade to learn more.",
                        tier.getDailyLimit(), tier.getDisplayName()
                    );
                    sendMessage(chatId, rateLimitMessage);
                    return;
                }
                
                // Check user status
                if (user.getStatus() == User.UserStatus.BANNED) {
                    sendMessage(chatId, "🚫 Your account has been suspended. Contact support for assistance.");
                    return;
                }
                
                // Process command
                String response = processCommand(messageText, chatId, user);
                sendMessage(chatId, response);
                
                // Record successful request
                userService.recordUserRequest(telegramId);
                monitoringService.recordSuccessfulRequest(System.currentTimeMillis() - startTime);
                
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", e.getMessage(), e);
            monitoringService.recordFailedRequest(System.currentTimeMillis() - startTime);
            
            if (update.hasMessage()) {
                String chatId = update.getMessage().getChatId().toString();
                sendMessage(chatId, "❌ Sorry, I encountered an error. Please try again later.");
            }
        }
    }
    
    /**
     * Process incoming commands and route to appropriate handlers
     */
    private String processCommand(String command, String chatId, User user) {
        command = command.trim().toLowerCase();
        Long userId = user.getTelegramId();
        
        try {
            if (command.equals("/start")) {
                return getWelcomeMessage(user);
            } else if (command.equals("/help")) {
                return getHelpMessage();
            } else if (command.equals("/scan")) {
                return analysisService.performMarketScan();
            } else if (command.equals("/market")) {
                return simpleScanService.getMarketStatus();
            } else if (command.startsWith("/analyze ")) {
                String symbol = command.substring(9).trim().toUpperCase();
                return analysisService.analyzeStock(symbol);
            } else if (command.equals("/portfolio")) {
                return getPortfolioSummary(userId);
            } else if (command.startsWith("/buy ")) {
                return processBuyCommand(command, userId);
            } else if (command.startsWith("/sell ")) {
                return processSellCommand(command, userId);
            } else if (command.equals("/watchlist")) {
                return getWatchlist(userId);
            } else if (command.startsWith("/watch ")) {
                String symbol = command.substring(7).trim().toUpperCase();
                return addToWatchlist(userId, symbol);
            } else if (command.startsWith("/unwatch ")) {
                String symbol = command.substring(9).trim().toUpperCase();
                return removeFromWatchlist(userId, symbol);
            } else if (command.equals("/profile")) {
                return getUserProfile(user);
            } else if (command.equals("/upgrade")) {
                return getUpgradeInfo();
            } else if (command.equals("/stats")) {
                return getUserStats(user);
            } else {
                return "❌ Unknown command. Type /help for available commands.";
            }
        } catch (Exception e) {
            logger.error("Error processing command '{}' for user {}: {}", command, userId, e.getMessage());
            return "❌ Error processing command. Please try again.";
        }
    }
    
    /**
     * Enhanced welcome message with user information
     */
    private String getWelcomeMessage(User user) {
        return String.format(
            "👋 Welcome %s!\n\n" +
            "🤖 I'm your NSE Stock Analysis Bot with advanced features!\n\n" +
            "📊 **Your Account:**\n" +
            "• Subscription: %s (%d requests/day)\n" +
            "• Requests today: %d\n\n" +
            "💡 **Available Commands:**\n" +
            "📈 `/scan` - Get top stock recommendations\n" +
            "🔍 `/analyze SYMBOL` - Analyze specific stock\n" +
            "💼 `/portfolio` - View your portfolio\n" +
            "👀 `/watchlist` - Manage your watchlist\n" +
            "📋 `/profile` - View your profile\n" +
            "⭐ `/upgrade` - Upgrade subscription\n" +
            "❓ `/help` - Show detailed help\n\n" +
            "Start by typing `/scan` to see today's top picks! 🚀",
            user.getDisplayName(),
            user.getSubscriptionTier().getDisplayName(),
            user.getSubscriptionTier().getDailyLimit(),
            user.getDailyRequestCount()
        );
    }
    
    /**
     * Get portfolio summary for user
     */
    private String getPortfolioSummary(Long userId) {
        try {
            var portfolio = portfolioService.getUserPortfolio(userId);
            var summary = portfolioService.getPortfolioSummary(userId);
            
            if (portfolio.isEmpty()) {
                return "📈 **Your Portfolio**\n\n" +
                       "Your portfolio is empty. Start tracking your investments!\n\n" +
                       "Use: `/buy SYMBOL QUANTITY PRICE` to add positions\n" +
                       "Example: `/buy RELIANCE 100 2500`";
            }
            
            StringBuilder response = new StringBuilder();
            response.append("💼 **Portfolio Summary**\n\n");
            response.append(String.format("📊 Total Positions: %d\n", summary.getTotalPositions()));
            response.append(String.format("💰 Total Investment: ₹%.2f\n", summary.getTotalInvestment()));
            response.append(String.format("📈 Current Value: ₹%.2f\n", summary.getCurrentValue()));
            response.append(String.format("📊 Unrealized P&L: %s₹%.2f (%.2f%%)\n\n", 
                                        summary.isProfit() ? "🟢+" : "🔴", 
                                        summary.getTotalUnrealizedPnL(), 
                                        summary.getAvgPnLPercentage()));
            
            response.append("📋 **Positions:**\n");
            for (var position : portfolio) {
                String pnlIcon = position.isProfit() ? "🟢" : (position.isLoss() ? "🔴" : "⚪");
                response.append(String.format("• %s: %s shares @ ₹%.2f %s\n",
                                            position.getSymbol(),
                                            position.getQuantity(),
                                            position.getAvgBuyPrice(),
                                            pnlIcon));
            }
            
            return response.toString();
            
        } catch (Exception e) {
            logger.error("Error getting portfolio for user {}: {}", userId, e.getMessage());
            return "❌ Error retrieving portfolio. Please try again.";
        }
    }
    
    /**
     * Process buy command
     */
    private String processBuyCommand(String command, Long userId) {
        try {
            // Parse: /buy SYMBOL QUANTITY PRICE
            String[] parts = command.split("\\s+");
            if (parts.length != 4) {
                return "❌ Invalid format. Use: `/buy SYMBOL QUANTITY PRICE`\n" +
                       "Example: `/buy RELIANCE 100 2500`";
            }
            
            String symbol = parts[1].toUpperCase();
            var quantity = new java.math.BigDecimal(parts[2]);
            var price = new java.math.BigDecimal(parts[3]);
            
            portfolioService.addPosition(userId, symbol, quantity, price);
            
            return String.format("✅ Added %s shares of %s @ ₹%.2f to your portfolio!\n\n" +
                                "Use `/portfolio` to view your updated portfolio.",
                                quantity, symbol, price);
                                
        } catch (Exception e) {
            return "❌ Error processing buy command. Please check format and try again.";
        }
    }
    
    /**
     * Process sell command
     */
    private String processSellCommand(String command, Long userId) {
        try {
            // Parse: /sell SYMBOL QUANTITY
            String[] parts = command.split("\\s+");
            if (parts.length != 3) {
                return "❌ Invalid format. Use: `/sell SYMBOL QUANTITY`\n" +
                       "Example: `/sell RELIANCE 50`";
            }
            
            String symbol = parts[1].toUpperCase();
            var quantity = new java.math.BigDecimal(parts[2]);
            
            boolean success = portfolioService.updatePosition(userId, symbol, quantity);
            
            if (success) {
                return String.format("✅ Sold %s shares of %s from your portfolio!\n\n" +
                                    "Use `/portfolio` to view your updated portfolio.",
                                    quantity, symbol);
            } else {
                return String.format("❌ No position found for %s or insufficient quantity.", symbol);
            }
                                
        } catch (Exception e) {
            return "❌ Error processing sell command. Please check format and try again.";
        }
    }
    
    /**
     * Get user watchlist
     */
    private String getWatchlist(Long userId) {
        var watchlist = userService.getUserWatchlist(userId);
        
        if (watchlist.isEmpty()) {
            return "👀 **Your Watchlist**\n\n" +
                   "Your watchlist is empty.\n\n" +
                   "Add stocks: `/watch SYMBOL`\n" +
                   "Example: `/watch RELIANCE`";
        }
        
        StringBuilder response = new StringBuilder("👀 **Your Watchlist**\n\n");
        for (String symbol : watchlist) {
            response.append(String.format("• %s\n", symbol));
        }
        response.append("\nUse `/analyze SYMBOL` to get detailed analysis!");
        
        return response.toString();
    }
    
    /**
     * Add to watchlist
     */
    private String addToWatchlist(Long userId, String symbol) {
        boolean success = userService.addToWatchlist(userId, symbol);
        
        if (success) {
            return String.format("✅ Added %s to your watchlist!\n\nUse `/watchlist` to view all watched stocks.", symbol);
        } else {
            return "❌ Failed to add to watchlist. Please try again.";
        }
    }
    
    /**
     * Remove from watchlist
     */
    private String removeFromWatchlist(Long userId, String symbol) {
        boolean success = userService.removeFromWatchlist(userId, symbol);
        
        if (success) {
            return String.format("✅ Removed %s from your watchlist!", symbol);
        } else {
            return "❌ Symbol not found in watchlist.";
        }
    }
    
    /**
     * Get user profile
     */
    private String getUserProfile(User user) {
        return String.format(
            "👤 **Your Profile**\n\n" +
            "**Account Details:**\n" +
            "• Name: %s\n" +
            "• Subscription: %s\n" +
            "• Status: %s\n" +
            "• Daily Limit: %d requests\n" +
            "• Used Today: %d requests\n\n" +
            "**Settings:**\n" +
            "• Language: %s\n" +
            "• Timezone: %s\n" +
            "• Notifications: %s\n" +
            "• Watchlist: %d stocks\n\n" +
            "**Joined:** %s",
            user.getDisplayName(),
            user.getSubscriptionTier().getDisplayName(),
            user.getStatus().name(),
            user.getSubscriptionTier().getDailyLimit(),
            user.getDailyRequestCount(),
            user.getPreferredLanguage(),
            user.getTimezone(),
            user.isNotificationsEnabled() ? "Enabled" : "Disabled",
            user.getWatchlist().size(),
            user.getCreatedAt().toLocalDate()
        );
    }
    
    /**
     * Get upgrade information
     */
    private String getUpgradeInfo() {
        return "⭐ **Subscription Plans**\n\n" +
               "🆓 **Free Tier**\n" +
               "• 10 requests per day\n" +
               "• Basic analysis\n" +
               "• Portfolio tracking\n\n" +
               "💎 **Premium Tier**\n" +
               "• 100 requests per day\n" +
               "• Advanced analysis\n" +
               "• Real-time alerts\n" +
               "• Priority support\n\n" +
               "🚀 **Pro Tier**\n" +
               "• 500 requests per day\n" +
               "• All Premium features\n" +
               "• Custom analysis\n" +
               "• API access\n\n" +
               "Contact @admin to upgrade your subscription!";
    }
    
    /**
     * Get user statistics
     */
    private String getUserStats(User user) {
        try {
            var portfolioSummary = portfolioService.getPortfolioSummary(user.getTelegramId());
            var watchlistSize = user.getWatchlist().size();
            
            return String.format(
                "📊 **Your Statistics**\n\n" +
                "**Activity:**\n" +
                "• Requests Today: %d / %d\n" +
                "• Member Since: %s\n" +
                "• Account Status: %s\n\n" +
                "**Portfolio:**\n" +
                "• Total Positions: %d\n" +
                "• Total Investment: ₹%.2f\n" +
                "• Current P&L: %s₹%.2f\n\n" +
                "**Watchlist:**\n" +
                "• Tracked Stocks: %d\n\n" +
                "**Subscription:**\n" +
                "• Current Plan: %s\n" +
                "• Daily Limit: %d requests",
                user.getDailyRequestCount(),
                user.getSubscriptionTier().getDailyLimit(),
                user.getCreatedAt().toLocalDate(),
                user.getStatus(),
                portfolioSummary.getTotalPositions(),
                portfolioSummary.getTotalInvestment(),
                portfolioSummary.isProfit() ? "🟢+" : "🔴",
                portfolioSummary.getTotalUnrealizedPnL(),
                watchlistSize,
                user.getSubscriptionTier().getDisplayName(),
                user.getSubscriptionTier().getDailyLimit()
            );
        } catch (Exception e) {
            logger.error("Error getting stats for user {}: {}", user.getTelegramId(), e.getMessage());
            return "❌ Error retrieving statistics. Please try again.";
        }
    }
    
    /**
     * Send message to user
     */
    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        
        try {
            execute(message);
            logger.debug("Message sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }
    
    /**
     * Welcome message for new users
     */
    private String getWelcomeMessage() {
        return """
            🚀 *Welcome to NSE Stock Analysis Bot!*
            
            I'm your AI-powered assistant for NSE stock analysis and trading recommendations.
            
            *Available Commands:*
            `/scan` - Get top 15-20 stock recommendations from Nifty 100
            `/analyze SYMBOL` - Analyze a specific stock (e.g., /analyze RELIANCE)
            `/help` - Show this help message
            
            *Features:*
            • Real-time NSE market data
            • ML-based analysis with multiple strategies
            • Technical analysis & news sentiment
            • Entry/exit points with confidence scores
            • Stop-loss and target price recommendations
            
            Ready to start trading smarter? Try `/scan` to see today's top picks! 📈
            """;
    }
    
    /**
     * Help message with detailed command information
     */
    private String getHelpMessage() {
        return """
            📖 **NSE Stock Analysis Bot - Complete Guide**
            
            **📈 Analysis Commands:**
            🔍 `/scan` - Get top 15-20 stock recommendations from Nifty 100
            📊 `/analyze SYMBOL` - Deep analysis of specific stock (e.g., /analyze RELIANCE)
            
            **💼 Portfolio Commands:**
            💰 `/portfolio` - View your portfolio summary and P&L
            🛒 `/buy SYMBOL QUANTITY PRICE` - Add stock to portfolio (e.g., /buy RELIANCE 100 2500)
            💸 `/sell SYMBOL QUANTITY` - Sell stock from portfolio (e.g., /sell RELIANCE 50)
            
            **👀 Watchlist Commands:**
            📋 `/watchlist` - View your tracked stocks
            ➕ `/watch SYMBOL` - Add stock to watchlist (e.g., /watch TCS)
            ➖ `/unwatch SYMBOL` - Remove stock from watchlist
            
            **👤 Account Commands:**
            📱 `/profile` - View your account profile and settings
            📊 `/stats` - View detailed account statistics
            ⭐ `/upgrade` - Learn about subscription plans
            ❓ `/help` - Show this help message
            
            **🎯 Features:**
            • Real-time NSE market data with ML-based analysis
            • Portfolio tracking with automatic P&L calculation
            • Personal watchlist management
            • Technical analysis & news sentiment
            • Rate limiting based on subscription tier
            • Entry/exit points with confidence scores
            
            **💎 Subscription Tiers:**
            🆓 **Free**: 10 requests/day
            💎 **Premium**: 100 requests/day + advanced features
            🚀 **Pro**: 500 requests/day + all features
            
            **⚠️ Disclaimer:**
            This bot provides analysis for educational purposes. Always do your own research and consult financial advisors before making investment decisions.
            
            Ready to start? Try `/scan` to see today's top picks! 🚀
            """;
    }
}