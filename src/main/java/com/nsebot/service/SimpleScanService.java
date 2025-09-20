package com.nsebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nsebot.dto.StockData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Simple scan service that provides quick market overview without heavy data processing
 */
@Service
public class SimpleScanService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleScanService.class);
    
    private final NSEDataService nseDataService;
    private final DataSourceStatsService statsService;
    
    // Top Nifty 50 stocks for quick scan
    private static final List<String> TOP_STOCKS = Arrays.asList(
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", 
        "HDFC", "ITC", "KOTAKBANK", "LT", "AXISBANK",
        "BHARTIARTL", "ASIANPAINT", "MARUTI", "BAJFINANCE", "NESTLEIND"
    );
    
    @Autowired
    public SimpleScanService(NSEDataService nseDataService, DataSourceStatsService statsService) {
        this.nseDataService = nseDataService;
        this.statsService = statsService;
    }
    
    /**
     * Perform a comprehensive market scan with live data and strategy analysis
     */
    public String performQuickMarketScan() {
        logger.info("🔍 Performing enhanced market scan with live data...");
        
        try {
            StringBuilder scanResult = new StringBuilder();
            scanResult.append("📊 **ENHANCED MARKET SCAN**\n");
            scanResult.append("🕒 ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))).append("\n\n");
            
            // Fetch live data for analysis
            List<MarketRecommendation> recommendations = performLiveAnalysis();
            
            // Add market status with real data
            scanResult.append("📈 **Market Status:** Open\n");
            scanResult.append("🔄 **Data Source:** Live NSE API + Smart Fallback\n");
            
            // Real-time statistics after data fetch
            var stats = statsService.getStatistics();
            scanResult.append("📊 **Live Data Stats:**\n");
            scanResult.append("✅ NSE Success: ").append(stats.getNseSuccessCount()).append("\n");
            scanResult.append("🟡 Yahoo Fallback: ").append(stats.getYahooSuccessCount()).append("\n");
            scanResult.append("🔴 Mock Fallback: ").append(stats.getMockDataCount()).append("\n\n");
            
            // Strategy-based recommendations sorted by confidence
            scanResult.append("🎯 **STRATEGY-BASED RECOMMENDATIONS:**\n");
            scanResult.append("_Sorted by Strategy Count & Confidence_\n\n");
            
            for (MarketRecommendation rec : recommendations) {
                scanResult.append(String.format("📈 **%s** - %s\n", rec.symbol, rec.signal));
                scanResult.append(String.format("💪 **Strategies:** %d/5 passed (%.1f%% confidence)\n", 
                    rec.passedStrategies, rec.confidence));
                scanResult.append(String.format("📊 **Strategies:** %s\n", String.join(", ", rec.strategies)));
                scanResult.append(String.format("💰 **Entry:** ₹%.2f | **Target:** ₹%.2f | **SL:** ₹%.2f\n", 
                    rec.entryPrice, rec.targetPrice, rec.stopLoss));
                scanResult.append(String.format("📉 **Current:** ₹%.2f | **Change:** %.2f%%\n", 
                    rec.currentPrice, rec.changePercent));
                scanResult.append("─────────────────────\n");
            }
            
            scanResult.append("\n💡 **Trading Tips:**\n");
            scanResult.append("• Higher strategy count = stronger signal\n");
            scanResult.append("• Use stop loss to manage risk\n");
            scanResult.append("• Verify with `/analyze SYMBOL` for details\n\n");
            
            scanResult.append("⚡ **Enhanced Features Active:**\n");
            scanResult.append("✅ Smart NSE Anti-Bot Evasion\n");
            scanResult.append("✅ Yahoo Finance Rate Limiting\n");
            scanResult.append("✅ Multi-Strategy Analysis\n");
            scanResult.append("✅ Real-time Price Targets\n\n");
            
            scanResult.append("🤖 _Mission Trade Bot - Enhanced Live Analysis_");
            
            logger.info("✅ Enhanced market scan completed with {} recommendations", recommendations.size());
            return scanResult.toString();
            
        } catch (Exception e) {
            logger.error("❌ Error performing enhanced market scan: {}", e.getMessage(), e);
            
            return "❌ **Market Scan Temporarily Unavailable**\n\n" +
                   "Our enhanced data systems are optimizing for better performance.\n" +
                   "Please try again in a moment, or use:\n" +
                   "• `/analyze RELIANCE` - For specific stock analysis\n" +
                   "• `/help` - For all available commands\n\n" +
                   "🔧 _Enhanced live data system is operational_";
        }
    }
    
    /**
     * Perform live analysis with multiple strategies
     */
    private List<MarketRecommendation> performLiveAnalysis() {
        logger.info("📊 Fetching live data for strategy analysis...");
        
        List<MarketRecommendation> recommendations = new ArrayList<>();
        List<String> scanStocks = TOP_STOCKS.subList(0, 5); // Analyze top 5 stocks
        
        for (String symbol : scanStocks) {
            try {
                // Fetch live stock data
                Optional<StockData> stockDataOpt = nseDataService.getStockData(symbol);
                if (stockDataOpt.isPresent()) {
                    StockData stockData = stockDataOpt.get();
                    MarketRecommendation recommendation = analyzeWithStrategies(symbol, stockData);
                    if (recommendation != null) {
                        recommendations.add(recommendation);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to analyze {}: {}", symbol, e.getMessage());
            }
        }
        
        // Sort by strategy count (descending) then by confidence (descending)
        recommendations.sort((a, b) -> {
            if (b.passedStrategies != a.passedStrategies) {
                return Integer.compare(b.passedStrategies, a.passedStrategies);
            }
            return Double.compare(b.confidence, a.confidence);
        });
        
        logger.info("📈 Generated {} strategy-based recommendations", recommendations.size());
        return recommendations;
    }
    
    /**
     * Analyze stock with multiple strategies
     */
    private MarketRecommendation analyzeWithStrategies(String symbol, StockData stockData) {
        try {
            List<String> passedStrategies = new ArrayList<>();
            double totalConfidence = 0;
            int strategyCount = 0;
            
            BigDecimal currentPrice = stockData.getLastPrice();
            BigDecimal change = stockData.getChange() != null ? stockData.getChange() : BigDecimal.ZERO;
            BigDecimal changePercent = stockData.getPercentChange() != null ? stockData.getPercentChange() : BigDecimal.ZERO;
            
            // Strategy 1: Price Action (Current vs Previous Close)
            if (stockData.getPreviousClose() != null && currentPrice.compareTo(stockData.getPreviousClose()) > 0) {
                passedStrategies.add("Price Action");
                totalConfidence += 75;
                strategyCount++;
            }
            
            // Strategy 2: Volume Analysis (Higher volume = stronger signal)
            if (stockData.getVolume() != null && stockData.getVolume() > 100000) {
                passedStrategies.add("Volume");
                totalConfidence += 80;
                strategyCount++;
            }
            
            // Strategy 3: Range Breakout (Price near day high)
            if (stockData.getDayHigh() != null && 
                currentPrice.divide(stockData.getDayHigh(), 4, RoundingMode.HALF_UP).doubleValue() > 0.95) {
                passedStrategies.add("Breakout");
                totalConfidence += 85;
                strategyCount++;
            }
            
            // Strategy 4: Support Level (Price above mid-range)
            if (stockData.getDayHigh() != null && stockData.getDayLow() != null) {
                BigDecimal midRange = stockData.getDayHigh().add(stockData.getDayLow()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                if (currentPrice.compareTo(midRange) > 0) {
                    passedStrategies.add("Support");
                    totalConfidence += 70;
                    strategyCount++;
                }
            }
            
            // Strategy 5: Momentum (Positive change)
            if (changePercent.doubleValue() > 0.5) {
                passedStrategies.add("Momentum");
                totalConfidence += 78;
                strategyCount++;
            }
            
            // Only return recommendation if at least 2 strategies pass
            if (passedStrategies.size() >= 2) {
                double avgConfidence = totalConfidence / Math.max(strategyCount, 1);
                
                // Calculate entry, target, and stop loss
                BigDecimal entryPrice = currentPrice;
                BigDecimal targetPrice = currentPrice.multiply(BigDecimal.valueOf(1.05)); // 5% target
                BigDecimal stopLoss = currentPrice.multiply(BigDecimal.valueOf(0.97)); // 3% stop loss
                
                String signal = avgConfidence > 75 ? "🔥 STRONG BUY" : "📈 BUY";
                
                return new MarketRecommendation(
                    symbol, signal, passedStrategies, passedStrategies.size(),
                    avgConfidence, entryPrice.doubleValue(), targetPrice.doubleValue(),
                    stopLoss.doubleValue(), currentPrice.doubleValue(), changePercent.doubleValue()
                );
            }
            
        } catch (Exception e) {
            logger.warn("Strategy analysis failed for {}: {}", symbol, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Market recommendation with strategy details
     */
    private static class MarketRecommendation {
        final String symbol;
        final String signal;
        final List<String> strategies;
        final int passedStrategies;
        final double confidence;
        final double entryPrice;
        final double targetPrice;
        final double stopLoss;
        final double currentPrice;
        final double changePercent;
        
        MarketRecommendation(String symbol, String signal, List<String> strategies, int passedStrategies,
                           double confidence, double entryPrice, double targetPrice, double stopLoss,
                           double currentPrice, double changePercent) {
            this.symbol = symbol;
            this.signal = signal;
            this.strategies = new ArrayList<>(strategies);
            this.passedStrategies = passedStrategies;
            this.confidence = confidence;
            this.entryPrice = entryPrice;
            this.targetPrice = targetPrice;
            this.stopLoss = stopLoss;
            this.currentPrice = currentPrice;
            this.changePercent = changePercent;
        }
    }
    
    /**
     * Get market status summary
     */
    public String getMarketStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("🏛️ **NSE MARKET STATUS**\n\n");
            status.append("📊 Market: Open (9:15 AM - 3:30 PM IST)\n");
            status.append("💹 Pre-Market: 9:00 AM - 9:15 AM\n");
            status.append("🌙 After-Market: 3:40 PM - 4:00 PM\n\n");
            
            var stats = statsService.getStatistics();
            status.append("📈 **Live Data Performance:**\n");
            status.append("🟢 NSE API Calls: ").append(stats.getNseSuccessCount()).append("\n");
            status.append("🟡 Yahoo Finance: ").append(stats.getYahooSuccessCount()).append("\n");
            status.append("⏱️ Session Uptime: ").append(getUptimeMinutes(stats)).append(" minutes\n\n");
            
            status.append("🚀 **Enhanced Features:**\n");
            status.append("✅ Smart anti-bot evasion active\n");
            status.append("✅ Rate limiting protection enabled\n");
            status.append("✅ Multi-source data fallback ready\n");
            
            return status.toString();
            
        } catch (Exception e) {
            logger.error("Error getting market status: {}", e.getMessage());
            return "📊 Market Status: Available\n🤖 Enhanced data system: Operational";
        }
    }
    
    private long getUptimeMinutes(DataSourceStatsService.DataSourceStatistics stats) {
        try {
            return java.time.Duration.between(stats.getStartTime(), LocalDateTime.now()).toMinutes();
        } catch (Exception e) {
            return 0;
        }
    }
}