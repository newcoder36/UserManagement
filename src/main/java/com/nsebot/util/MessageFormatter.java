package com.nsebot.util;

import com.nsebot.entity.StockAnalysis;
import com.nsebot.dto.StockData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for formatting messages for Telegram bot responses
 */
@Component
public class MessageFormatter {
    
    private final NumberFormat currencyFormatter;
    private final NumberFormat percentFormatter;
    private final DateTimeFormatter timeFormatter;
    
    public MessageFormatter() {
        this.currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        this.percentFormatter = NumberFormat.getPercentInstance(Locale.US);
        this.percentFormatter.setMaximumFractionDigits(2);
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd-MMM-yyyy");
    }
    
    /**
     * Format stock analysis for Telegram message
     */
    public String formatStockAnalysis(StockAnalysis analysis, StockData currentData) {
        StringBuilder message = new StringBuilder();
        
        message.append(String.format("📊 *Analysis: %s*\n\n", analysis.getSymbol()));
        
        // Current market data
        if (currentData != null) {
            message.append("*💰 Current Market Data:*\n");
            message.append(String.format("Price: %s", formatPrice(currentData.getLastPrice())));
            
            if (currentData.getPercentChange() != null) {
                String changeIcon = currentData.getPercentChange().compareTo(BigDecimal.ZERO) >= 0 ? "↗️" : "↘️";
                message.append(String.format(" (%s%% %s)\n", 
                             formatPercent(currentData.getPercentChange()), changeIcon));
            } else {
                message.append("\n");
            }
            
            if (currentData.getVolume() != null) {
                message.append(String.format("Volume: %s\n", formatVolume(currentData.getVolume())));
            }
            message.append("\n");
        }
        
        // Trading recommendation
        message.append("*🎯 Trading Recommendation:*\n");
        message.append(String.format("**Action:** %s %s\n", 
                     formatRecommendation(analysis.getRecommendation()),
                     getRecommendationIcon(analysis.getRecommendation())));
        
        if (analysis.getEntryPrice() != null) {
            message.append(String.format("**Entry:** %s\n", formatPrice(analysis.getEntryPrice())));
        }
        
        if (analysis.getTargetPrice1() != null) {
            message.append(String.format("**Target 1:** %s", formatPrice(analysis.getTargetPrice1())));
            if (analysis.getEntryPrice() != null) {
                BigDecimal riskReward = calculateRiskReward(analysis.getEntryPrice(), 
                                                          analysis.getTargetPrice1(), 
                                                          analysis.getStopLoss());
                if (riskReward != null) {
                    message.append(String.format(" (R:R 1:%.1f)", riskReward));
                }
            }
            message.append("\n");
        }
        
        if (analysis.getTargetPrice2() != null) {
            message.append(String.format("**Target 2:** %s\n", formatPrice(analysis.getTargetPrice2())));
        }
        
        if (analysis.getStopLoss() != null) {
            message.append(String.format("**Stop Loss:** %s\n", formatPrice(analysis.getStopLoss())));
        }
        
        message.append("\n");
        
        // ML Confidence and strategies
        message.append("*🤖 Analysis Results:*\n");
        if (analysis.getConfidenceScore() != null) {
            message.append(String.format("**Confidence:** %s%% %s\n", 
                         analysis.getConfidenceScore().setScale(0, RoundingMode.HALF_UP),
                         getConfidenceIcon(analysis.getConfidenceScore())));
        }
        
        if (analysis.getStrategiesPassed() != null && analysis.getTotalStrategies() != null) {
            message.append(String.format("**Strategies Passed:** %d/%d\n", 
                         analysis.getStrategiesPassed(), analysis.getTotalStrategies()));
        }
        
        // List passed strategies
        if (analysis.getPassedStrategies() != null && !analysis.getPassedStrategies().isEmpty()) {
            message.append("\n*✅ Passed Strategies:*\n");
            for (String strategy : analysis.getPassedStrategies()) {
                message.append(String.format("• %s\n", strategy));
            }
        }
        
        // Time and risk information
        message.append("\n");
        if (analysis.getTimeHorizonHours() != null) {
            message.append(String.format("*⏰ Time Horizon:* %d hours\n", analysis.getTimeHorizonHours()));
        }
        
        if (analysis.getRiskLevel() != null) {
            message.append(String.format("*⚠️ Risk Level:* %s %s\n", 
                         analysis.getRiskLevel(),
                         getRiskIcon(analysis.getRiskLevel())));
        }
        
        // Timestamp
        message.append(String.format("\n*Last updated:* %s", 
                     analysis.getAnalyzedAt().format(timeFormatter)));
        
        return message.toString();
    }
    
    /**
     * Format price with currency symbol
     */
    public String formatPrice(BigDecimal price) {
        if (price == null) return "N/A";
        return "₹" + price.setScale(2, RoundingMode.HALF_UP).toString();
    }
    
    /**
     * Format percentage
     */
    public String formatPercent(BigDecimal percent) {
        if (percent == null) return "0.00";
        return percent.setScale(2, RoundingMode.HALF_UP).toString();
    }
    
    /**
     * Format volume with appropriate units
     */
    public String formatVolume(Long volume) {
        if (volume == null) return "N/A";
        
        if (volume >= 10_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 100_000) {
            return String.format("%.0fK", volume / 1_000.0);
        } else {
            return NumberFormat.getInstance().format(volume);
        }
    }
    
    /**
     * Format recommendation text
     */
    public String formatRecommendation(StockAnalysis.Recommendation recommendation) {
        if (recommendation == null) return "HOLD";
        
        return switch (recommendation) {
            case STRONG_BUY -> "STRONG BUY";
            case BUY -> "BUY";
            case HOLD -> "HOLD";
            case SELL -> "SELL";
            case STRONG_SELL -> "STRONG SELL";
        };
    }
    
    /**
     * Get icon for recommendation
     */
    public String getRecommendationIcon(StockAnalysis.Recommendation recommendation) {
        if (recommendation == null) return "⏸️";
        
        return switch (recommendation) {
            case STRONG_BUY -> "🚀";
            case BUY -> "📈";
            case HOLD -> "⏸️";
            case SELL -> "📉";
            case STRONG_SELL -> "🔴";
        };
    }
    
    /**
     * Get icon for confidence level
     */
    public String getConfidenceIcon(BigDecimal confidence) {
        if (confidence == null) return "❓";
        
        int conf = confidence.intValue();
        if (conf >= 85) return "🔥";
        if (conf >= 70) return "✅";
        if (conf >= 60) return "⚡";
        return "⚠️";
    }
    
    /**
     * Get icon for risk level
     */
    public String getRiskIcon(StockAnalysis.RiskLevel riskLevel) {
        if (riskLevel == null) return "❓";
        
        return switch (riskLevel) {
            case LOW -> "🟢";
            case MEDIUM -> "🟡";
            case HIGH -> "🟠";
            case VERY_HIGH -> "🔴";
        };
    }
    
    /**
     * Calculate risk-reward ratio
     */
    private BigDecimal calculateRiskReward(BigDecimal entry, BigDecimal target, BigDecimal stopLoss) {
        if (entry == null || target == null || stopLoss == null) {
            return null;
        }
        
        BigDecimal profit = target.subtract(entry);
        BigDecimal loss = entry.subtract(stopLoss);
        
        if (loss.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        return profit.divide(loss, 2, RoundingMode.HALF_UP);
    }
}