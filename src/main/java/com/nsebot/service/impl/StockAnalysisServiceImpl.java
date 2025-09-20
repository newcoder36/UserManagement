package com.nsebot.service.impl;

import com.nsebot.service.StockAnalysisService;
import com.nsebot.service.NSEDataService;
import com.nsebot.service.DynamicMarketScannerService;
import com.nsebot.analysis.TechnicalAnalysisService;
import com.nsebot.analysis.news.NewsSentimentService;
import com.nsebot.analysis.ml.MLPredictionService;
import com.nsebot.dto.StockData;
import com.nsebot.entity.StockAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;

/**
 * Implementation of Stock Analysis Service
 * Handles market scanning and individual stock analysis
 */
@Service
public class StockAnalysisServiceImpl implements StockAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(StockAnalysisServiceImpl.class);
    
    private final NSEDataService nseDataService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final NewsSentimentService newsSentimentService;
    private final MLPredictionService mlPredictionService;
    private final DynamicMarketScannerService dynamicMarketScannerService;
    
    @Autowired
    public StockAnalysisServiceImpl(NSEDataService nseDataService,
                                   TechnicalAnalysisService technicalAnalysisService,
                                   NewsSentimentService newsSentimentService,
                                   MLPredictionService mlPredictionService,
                                   DynamicMarketScannerService dynamicMarketScannerService) {
        this.nseDataService = nseDataService;
        this.technicalAnalysisService = technicalAnalysisService;
        this.newsSentimentService = newsSentimentService;
        this.mlPredictionService = mlPredictionService;
        this.dynamicMarketScannerService = dynamicMarketScannerService;
    }
    
    // Top Nifty 100 stocks for market scanning
    private static final List<String> NIFTY_100_SYMBOLS = Arrays.asList(
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "HINDUNILVR", "ICICIBANK", "HDFC", "ITC",
        "KOTAKBANK", "LT", "AXISBANK", "BHARTIARTL", "ASIANPAINT", "MARUTI", "BAJFINANCE",
        "NESTLEIND", "HCLTECH", "WIPRO", "ULTRACEMCO", "ADANIENT", "SUNPHARMA", "TITAN",
        "POWERGRID", "M&M", "TECHM", "NTPC", "BAJAJFINSV", "ONGC", "TATASTEEL", "INDUSINDBK",
        "ADANIPORTS", "COALINDIA", "JSWSTEEL", "BRITANNIA", "GRASIM", "HINDALCO", "SBIN",
        "HDFCLIFE", "DRREDDY", "BPCL", "EICHERMOT", "APOLLOHOSP", "TATACONSUM", "BAJAJ-AUTO",
        "CIPLA", "SBILIFE", "DIVISLAB", "HEROMOTOCO", "UPL", "DABUR", "GODREJCP", "ICICIPRULI",
        "LTIM", "PIDILITIND", "COLPAL", "TORNTPHARM", "BANKBARODA", "ADANITRANS", "SIEMENS",
        "BERGEPAINT", "AUROPHARMA", "IOC", "BAJAJHLDNG", "SHREECEM", "HAVELLS", "MCDOWELL-N",
        "MARICO", "BOSCHLTD", "LUPIN", "LICI", "ABB", "TATAMOTORS", "MPHASIS", "INDIGO",
        "NAUKRI", "CONCOR", "ZEEL", "TATAPOWER", "JUBLFOOD", "VOLTAS", "PFC", "SAIL",
        "PEL", "MOTHERSON", "HINDPETRO", "AMBUJACEM", "IDEA", "BEL", "NMDC", "RBLBANK",
        "GAIL", "RECLTD", "INDHOTEL", "LICHSGFIN"
    );
    
    @Override
    // @Cacheable(value = "marketScan", key = "'dynamic'", unless = "#result.contains('❌')") // Disabled for real-time data
    public String performMarketScan() {
        logger.info("🔍 Performing dynamic market scan with categorized analysis");
        
        try {
            // Get dynamic market scan with categorized stocks
            DynamicMarketScannerService.DynamicScanResult dynamicResult = 
                dynamicMarketScannerService.getDynamicTopPicks();
            
            // Convert dynamic results to our analysis format
            List<MarketScanResult> scanResults = performDynamicCategorizedAnalysis(dynamicResult);
            
            // Filter and sort by confidence and signals (lowered thresholds for better results)
            List<MarketScanResult> highConfidence = scanResults.stream()
                .filter(result -> result.confidence.compareTo(new BigDecimal("70")) >= 0)
                .sorted((a, b) -> b.confidence.compareTo(a.confidence)) // Sort by confidence descending
                .limit(10)  // Increased limit for better coverage
                .collect(Collectors.toList());
                
            List<MarketScanResult> mediumConfidence = scanResults.stream()
                .filter(result -> result.confidence.compareTo(new BigDecimal("50")) >= 0 && 
                                result.confidence.compareTo(new BigDecimal("70")) < 0)
                .sorted((a, b) -> b.confidence.compareTo(a.confidence)) // Sort by confidence descending
                .limit(10)  // Increased limit for better coverage
                .collect(Collectors.toList());
                
            logger.info("📊 Analysis Results - High: {}, Medium: {}, Total: {}", 
                highConfidence.size(), mediumConfidence.size(), scanResults.size());
            
            // Calculate market sentiment
            MarketSentiment sentiment = calculateMarketSentiment(scanResults);
            
            return formatDynamicMarketScanResults(dynamicResult, highConfidence, mediumConfidence, sentiment, scanResults.size());
            
        } catch (Exception e) {
            logger.error("❌ Error performing dynamic market scan", e);
            return "❌ Unable to perform market scan at the moment. Please try again later.";
        }
    }
    
    @Override
    // @Cacheable(value = "stockAnalysis", key = "#symbol.toUpperCase()", unless = "#result.contains('❌')") // Disabled for real-time data
    public String analyzeStock(String symbol) {
        logger.info("Performing comprehensive analysis for stock: {}", symbol);
        
        if (symbol == null || symbol.trim().isEmpty()) {
            return "❌ Please provide a valid stock symbol. Example: `/analyze RELIANCE`";
        }
        
        String upperSymbol = symbol.trim().toUpperCase();
        
        try {
            // Get historical stock data for analysis
            List<StockData> historicalData = nseDataService.getHistoricalData(upperSymbol, 50);
            if (historicalData.isEmpty()) {
                return String.format("❌ Unable to fetch data for %s. Please verify the symbol and try again.", upperSymbol);
            }
            
            // Perform comprehensive analysis
            TechnicalAnalysisService.TechnicalAnalysisResult technicalResult = 
                technicalAnalysisService.performTechnicalAnalysis(upperSymbol, historicalData);
            
            NewsSentimentService.NewsSentimentResult sentimentResult = 
                newsSentimentService.analyzeSentiment(upperSymbol);
            
            MLPredictionService.MLPredictionResult mlResult = 
                mlPredictionService.predictPriceMovement(upperSymbol, historicalData);
            
            // Calculate overall recommendation and confidence
            ComprehensiveAnalysis analysis = calculateComprehensiveAnalysis(
                technicalResult, sentimentResult, mlResult, historicalData);
            
            return formatDetailedAnalysis(upperSymbol, analysis, historicalData.get(historicalData.size() - 1));
            
        } catch (Exception e) {
            logger.error("Error analyzing stock: {}", upperSymbol, e);
            return String.format("❌ Unable to analyze %s at the moment. Please try again later.", upperSymbol);
        }
    }
    
    private List<MarketScanResult> performComprehensiveAnalysis() {
        return NIFTY_100_SYMBOLS.parallelStream()
            .map(symbol -> {
                try {
                    List<StockData> data = nseDataService.getHistoricalData(symbol, 30);
                    if (data.isEmpty()) return null;
                    
                    TechnicalAnalysisService.TechnicalAnalysisResult technical = 
                        technicalAnalysisService.performTechnicalAnalysis(symbol, data);
                    NewsSentimentService.NewsSentimentResult sentiment = 
                        newsSentimentService.analyzeSentiment(symbol);
                    MLPredictionService.MLPredictionResult ml = 
                        mlPredictionService.predictPriceMovement(symbol, data);
                    
                    return createMarketScanResult(symbol, technical, sentiment, ml, data.get(data.size() - 1));
                } catch (Exception e) {
                    logger.warn("Error analyzing {} during market scan: {}", symbol, e.getMessage());
                    return null;
                }
            })
            .filter(result -> result != null)
            .filter(result -> !result.recommendation.equals(StockAnalysis.Recommendation.HOLD))
            .sorted((a, b) -> b.confidence.compareTo(a.confidence))
            .collect(Collectors.toList());
    }
    
    /**
     * Perform comprehensive analysis on dynamically categorized stocks
     */
    private List<MarketScanResult> performDynamicCategorizedAnalysis(DynamicMarketScannerService.DynamicScanResult dynamicResult) {
        logger.info("📊 Analyzing {} dynamically selected stocks across all market cap categories", dynamicResult.getTotalStocks());
        
        List<MarketScanResult> allResults = new ArrayList<>();
        
        // Analyze stocks from each category
        for (DynamicMarketScannerService.MarketCapCategory category : DynamicMarketScannerService.MarketCapCategory.values()) {
            List<DynamicMarketScannerService.LiveMarketStock> categoryStocks = dynamicResult.getCategoryPicks(category);
            
            logger.info("{} {} category: Analyzing {} stocks", category.getEmoji(), category.getDisplayName(), categoryStocks.size());
            
            List<MarketScanResult> categoryResults = categoryStocks.parallelStream()
                .map(liveStock -> {
                    try {
                        String symbol = liveStock.getSymbol();
                        List<StockData> data = nseDataService.getHistoricalData(symbol, 30);
                        if (data.isEmpty()) {
                            logger.debug("No historical data for {}", symbol);
                            return null;
                        }
                        
                        TechnicalAnalysisService.TechnicalAnalysisResult technical = 
                            technicalAnalysisService.performTechnicalAnalysis(symbol, data);
                        NewsSentimentService.NewsSentimentResult sentiment = 
                            newsSentimentService.analyzeSentiment(symbol);
                        MLPredictionService.MLPredictionResult ml = 
                            mlPredictionService.predictPriceMovement(symbol, data);
                        
                        MarketScanResult result = createMarketScanResult(symbol, technical, sentiment, ml, data.get(data.size() - 1));
                        result.marketCapCategory = category; // Add category info
                        return result;
                        
                    } catch (Exception e) {
                        logger.warn("Error analyzing {} from {} category: {}", liveStock.getSymbol(), category.getDisplayName(), e.getMessage());
                        return null;
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());
                
            allResults.addAll(categoryResults);
            logger.info("✅ Completed analysis for {} category: {}/{} stocks analyzed successfully", 
                category.getDisplayName(), categoryResults.size(), categoryStocks.size());
        }
        
        // Filter and sort results
        List<MarketScanResult> filteredResults = allResults.stream()
            .filter(result -> !result.recommendation.equals(StockAnalysis.Recommendation.HOLD))
            .sorted((a, b) -> b.confidence.compareTo(a.confidence))
            .collect(Collectors.toList());
            
        logger.info("🎯 Dynamic analysis complete: {}/{} stocks passed filters", filteredResults.size(), allResults.size());
        return filteredResults;
    }
    
    private MarketScanResult createMarketScanResult(String symbol, 
                                                   TechnicalAnalysisService.TechnicalAnalysisResult technical,
                                                   NewsSentimentService.NewsSentimentResult sentiment,
                                                   MLPredictionService.MLPredictionResult ml,
                                                   StockData currentData) {
        
        // Calculate combined confidence and recommendation
        BigDecimal combinedConfidence = calculateCombinedConfidence(technical, sentiment, ml);
        StockAnalysis.Recommendation finalRecommendation = determineFinalRecommendation(technical, sentiment, ml);
        
        // Enhanced intraday target calculation based on volatility and ML prediction
        BigDecimal currentPrice = currentData.getLastPrice();
        BigDecimal volatility = calculateIntradayVolatility(currentPrice);
        BigDecimal mlTargetPrice = ml.getTargetPrice();
        
        // Improved entry and targets with ML integration
        BigDecimal entryPrice = currentPrice.multiply(new BigDecimal("0.9985")); // 0.15% below current
        
        // Dynamic targets based on ML prediction and technical signals
        BigDecimal baseTarget = mlTargetPrice != null && mlTargetPrice.compareTo(BigDecimal.ZERO) > 0 
            ? mlTargetPrice : currentPrice.multiply(new BigDecimal("1.02"));
        
        // Intraday targets: More aggressive for higher confidence
        BigDecimal target1;
        if (combinedConfidence.compareTo(new BigDecimal("85")) >= 0) {
            target1 = currentPrice.add(volatility.multiply(new BigDecimal("2.5"))); // High confidence: 2.5x volatility
        } else if (combinedConfidence.compareTo(new BigDecimal("70")) >= 0) {
            target1 = currentPrice.add(volatility.multiply(new BigDecimal("2.0"))); // Medium: 2x volatility
        } else {
            target1 = currentPrice.add(volatility.multiply(new BigDecimal("1.5"))); // Conservative: 1.5x volatility
        }
        
        // Ensure target is reasonable (1-4% for intraday)
        BigDecimal minTarget = currentPrice.multiply(new BigDecimal("1.01"));
        BigDecimal maxTarget = currentPrice.multiply(new BigDecimal("1.04"));
        target1 = target1.max(minTarget).min(maxTarget);
        
        // Smart stop loss based on volatility and support levels
        BigDecimal stopLoss = currentPrice.subtract(volatility.multiply(new BigDecimal("1.0")));
        BigDecimal maxStopLoss = currentPrice.multiply(new BigDecimal("0.985")); // Max 1.5% loss
        stopLoss = stopLoss.max(maxStopLoss);
        
        // Enhanced strategy list with ML integration
        List<String> passedStrategies = new ArrayList<>(technical.getPassedStrategies());
        
        // Add ML strategy if confident
        if (ml.getConfidence().compareTo(new BigDecimal("70")) >= 0) {
            passedStrategies.add("ML-" + ml.getDirection().toString().substring(0, 4));
        }
        
        // Add sentiment strategy if confident
        if (sentiment.getConfidence().compareTo(new BigDecimal("60")) >= 0) {
            passedStrategies.add("News-" + (sentiment.isBullish() ? "Bull" : "Bear"));
        }
        
        // If no strategies passed, add some basic ones based on price movement
        if (passedStrategies.isEmpty()) {
            if (currentData.getPercentChange() != null && currentData.getPercentChange().compareTo(BigDecimal.ZERO) > 0) {
                passedStrategies.add("Price-Momentum");
                passedStrategies.add("Intraday-Bullish");
            } else {
                passedStrategies.add("Value-Buy");
                passedStrategies.add("Support-Test");
            }
        }
        
        logger.debug("Strategies for {}: {}", symbol, String.join(", ", passedStrategies));
        
        return new MarketScanResult(symbol, finalRecommendation, combinedConfidence,
                                  currentPrice, entryPrice, target1, stopLoss, passedStrategies);
    }
    
    /**
     * Calculate intraday volatility for better target setting
     */
    private BigDecimal calculateIntradayVolatility(BigDecimal currentPrice) {
        // Intraday volatility estimation: typically 0.5-2% for most stocks
        // This is a simplified calculation - in production would use historical intraday data
        BigDecimal baseVolatility = currentPrice.multiply(new BigDecimal("0.015")); // 1.5% base
        
        // Adjust based on market conditions and time of day
        java.time.LocalTime now = java.time.LocalTime.now();
        if (now.isBefore(java.time.LocalTime.of(10, 30)) || now.isAfter(java.time.LocalTime.of(14, 30))) {
            // Higher volatility during opening and closing hours
            baseVolatility = baseVolatility.multiply(new BigDecimal("1.3"));
        }
        
        return baseVolatility;
    }
    
    private ComprehensiveAnalysis calculateComprehensiveAnalysis(TechnicalAnalysisService.TechnicalAnalysisResult technical,
                                                               NewsSentimentService.NewsSentimentResult sentiment,
                                                               MLPredictionService.MLPredictionResult ml,
                                                               List<StockData> historicalData) {
        
        BigDecimal combinedConfidence = calculateCombinedConfidence(technical, sentiment, ml);
        StockAnalysis.Recommendation finalRecommendation = determineFinalRecommendation(technical, sentiment, ml);
        
        StockData currentData = historicalData.get(historicalData.size() - 1);
        BigDecimal currentPrice = currentData.getLastPrice();
        
        // Enhanced intraday analysis with ML integration
        BigDecimal intradayVolatility = calculateIntradayVolatility(currentPrice);
        BigDecimal historicalVolatility = calculateVolatility(historicalData);
        BigDecimal avgVolatility = intradayVolatility.add(historicalVolatility).divide(new BigDecimal("2"));
        
        // ML-enhanced target calculation
        BigDecimal mlTargetPrice = ml.getTargetPrice();
        BigDecimal entryRange = currentPrice.multiply(new BigDecimal("0.003")); // 0.3% range for better entry
        
        // Dynamic targets based on confidence and ML predictions
        BigDecimal target1, target2, stopLoss;
        
        if (combinedConfidence.compareTo(new BigDecimal("80")) >= 0) {
            // High confidence: More aggressive targets
            target1 = mlTargetPrice != null && mlTargetPrice.compareTo(currentPrice) > 0 
                ? currentPrice.add(mlTargetPrice.subtract(currentPrice).multiply(new BigDecimal("0.6")))
                : currentPrice.add(avgVolatility.multiply(new BigDecimal("2.2")));
            target2 = mlTargetPrice != null && mlTargetPrice.compareTo(currentPrice) > 0 
                ? mlTargetPrice
                : currentPrice.add(avgVolatility.multiply(new BigDecimal("3.5")));
            stopLoss = currentPrice.subtract(avgVolatility.multiply(new BigDecimal("0.9")));
        } else if (combinedConfidence.compareTo(new BigDecimal("65")) >= 0) {
            // Medium confidence: Balanced approach
            target1 = currentPrice.add(avgVolatility.multiply(new BigDecimal("1.8")));
            target2 = currentPrice.add(avgVolatility.multiply(new BigDecimal("2.8")));
            stopLoss = currentPrice.subtract(avgVolatility.multiply(new BigDecimal("1.0")));
        } else {
            // Lower confidence: Conservative targets
            target1 = currentPrice.add(avgVolatility.multiply(new BigDecimal("1.4")));
            target2 = currentPrice.add(avgVolatility.multiply(new BigDecimal("2.2")));
            stopLoss = currentPrice.subtract(avgVolatility.multiply(new BigDecimal("1.1")));
        }
        
        // Ensure intraday targets are realistic (0.8% to 4% for target1, 1.5% to 6% for target2)
        BigDecimal minTarget1 = currentPrice.multiply(new BigDecimal("1.008"));
        BigDecimal maxTarget1 = currentPrice.multiply(new BigDecimal("1.040"));
        target1 = target1.max(minTarget1).min(maxTarget1);
        
        BigDecimal minTarget2 = currentPrice.multiply(new BigDecimal("1.015"));
        BigDecimal maxTarget2 = currentPrice.multiply(new BigDecimal("1.060"));
        target2 = target2.max(minTarget2).min(maxTarget2);
        
        // Smart stop loss: Maximum 1.8% loss for intraday
        BigDecimal maxStopLoss = currentPrice.multiply(new BigDecimal("0.982"));
        stopLoss = stopLoss.max(maxStopLoss);
        
        String riskAssessment = assessRisk(technical, sentiment, ml, avgVolatility);
        
        return new ComprehensiveAnalysis(finalRecommendation, combinedConfidence, currentPrice,
                                       currentPrice.subtract(entryRange), currentPrice.add(entryRange),
                                       target1, target2, stopLoss, technical, sentiment, ml, riskAssessment);
    }
    
    private BigDecimal calculateCombinedConfidence(TechnicalAnalysisService.TechnicalAnalysisResult technical,
                                                  NewsSentimentService.NewsSentimentResult sentiment,
                                                  MLPredictionService.MLPredictionResult ml) {
        // Weighted confidence calculation: Technical 50%, ML 30%, Sentiment 20%
        BigDecimal technicalWeight = new BigDecimal("0.5");
        BigDecimal mlWeight = new BigDecimal("0.3");
        BigDecimal sentimentWeight = new BigDecimal("0.2");
        
        BigDecimal combined = technical.getConfidence().multiply(technicalWeight)
                             .add(ml.getConfidence().multiply(mlWeight))
                             .add(sentiment.getConfidence().multiply(sentimentWeight));
        
        return combined.setScale(0, BigDecimal.ROUND_HALF_UP);
    }
    
    private StockAnalysis.Recommendation determineFinalRecommendation(TechnicalAnalysisService.TechnicalAnalysisResult technical,
                                                                     NewsSentimentService.NewsSentimentResult sentiment,
                                                                     MLPredictionService.MLPredictionResult ml) {
        
        // Score-based approach: BUY = +2/+1, SELL = -2/-1, HOLD = 0
        int score = 0;
        
        // Technical analysis (highest weight)
        switch (technical.getRecommendation()) {
            case STRONG_BUY -> score += 2;
            case BUY -> score += 1;
            case STRONG_SELL -> score -= 2;
            case SELL -> score -= 1;
        }
        
        // ML prediction
        if (ml.getDirection() == MLPredictionService.PredictionDirection.BULLISH) {
            score += ml.getConfidence().compareTo(new BigDecimal("80")) >= 0 ? 2 : 1;
        } else if (ml.getDirection() == MLPredictionService.PredictionDirection.BEARISH) {
            score -= ml.getConfidence().compareTo(new BigDecimal("80")) >= 0 ? 2 : 1;
        }
        
        // News sentiment (lowest weight)
        if (sentiment.isBullish() && sentiment.getConfidence().compareTo(new BigDecimal("60")) >= 0) {
            score += 1;
        } else if (sentiment.isBearish() && sentiment.getConfidence().compareTo(new BigDecimal("60")) >= 0) {
            score -= 1;
        }
        
        // Determine final recommendation
        return switch (score) {
            case 4, 5 -> StockAnalysis.Recommendation.STRONG_BUY;
            case 2, 3 -> StockAnalysis.Recommendation.BUY;
            case -4, -5 -> StockAnalysis.Recommendation.STRONG_SELL;
            case -2, -3 -> StockAnalysis.Recommendation.SELL;
            default -> StockAnalysis.Recommendation.HOLD;
        };
    }
    
    private BigDecimal calculateVolatility(List<StockData> data) {
        if (data.size() < 2) return new BigDecimal("10"); // Default volatility
        
        // Calculate average price change percentage over last 20 periods
        int periods = Math.min(20, data.size() - 1);
        BigDecimal sumSquaredReturns = BigDecimal.ZERO;
        
        for (int i = data.size() - periods; i < data.size(); i++) {
            BigDecimal currentPrice = data.get(i).getLastPrice();
            BigDecimal previousPrice = data.get(i - 1).getLastPrice();
            BigDecimal returnPct = currentPrice.subtract(previousPrice)
                                             .divide(previousPrice, 6, BigDecimal.ROUND_HALF_UP);
            sumSquaredReturns = sumSquaredReturns.add(returnPct.multiply(returnPct));
        }
        
        BigDecimal variance = sumSquaredReturns.divide(new BigDecimal(periods), 6, BigDecimal.ROUND_HALF_UP);
        return new BigDecimal(Math.sqrt(variance.doubleValue())).multiply(data.get(data.size() - 1).getLastPrice());
    }
    
    private String assessRisk(TechnicalAnalysisService.TechnicalAnalysisResult technical,
                             NewsSentimentService.NewsSentimentResult sentiment,
                             MLPredictionService.MLPredictionResult ml,
                             BigDecimal volatility) {
        
        int riskScore = 0;
        
        // High volatility = higher risk
        if (volatility.compareTo(new BigDecimal("30")) > 0) riskScore += 2;
        else if (volatility.compareTo(new BigDecimal("15")) > 0) riskScore += 1;
        
        // Conflicting signals = higher risk
        boolean conflictingSignals = isConflictingSignals(technical, sentiment, ml);
        if (conflictingSignals) riskScore += 2;
        
        // Low confidence = higher risk
        BigDecimal avgConfidence = calculateCombinedConfidence(technical, sentiment, ml);
        if (avgConfidence.compareTo(new BigDecimal("60")) < 0) riskScore += 1;
        
        return switch (riskScore) {
            case 0, 1 -> "LOW";
            case 2, 3 -> "MEDIUM";
            default -> "HIGH";
        };
    }
    
    private boolean isConflictingSignals(TechnicalAnalysisService.TechnicalAnalysisResult technical,
                                        NewsSentimentService.NewsSentimentResult sentiment,
                                        MLPredictionService.MLPredictionResult ml) {
        // Check if technical and ML predictions are opposite
        boolean techBullish = technical.getRecommendation() == StockAnalysis.Recommendation.BUY ||
                             technical.getRecommendation() == StockAnalysis.Recommendation.STRONG_BUY;
        boolean mlBullish = ml.getDirection() == MLPredictionService.PredictionDirection.BULLISH;
        
        return techBullish != mlBullish;
    }
    
    private MarketSentiment calculateMarketSentiment(List<MarketScanResult> results) {
        if (results.isEmpty()) return new MarketSentiment(0, 0, 100, "NEUTRAL");
        
        long bullishCount = results.stream()
            .mapToLong(r -> (r.recommendation == StockAnalysis.Recommendation.BUY || 
                           r.recommendation == StockAnalysis.Recommendation.STRONG_BUY) ? 1 : 0)
            .sum();
            
        long bearishCount = results.stream()
            .mapToLong(r -> (r.recommendation == StockAnalysis.Recommendation.SELL || 
                           r.recommendation == StockAnalysis.Recommendation.STRONG_SELL) ? 1 : 0)
            .sum();
            
        long neutralCount = results.size() - bullishCount - bearishCount;
        
        int bullishPct = (int) ((bullishCount * 100) / results.size());
        int bearishPct = (int) ((bearishCount * 100) / results.size());
        int neutralPct = (int) ((neutralCount * 100) / results.size());
        
        String overall = bullishPct > 60 ? "BULLISH" : bearishPct > 40 ? "BEARISH" : "NEUTRAL";
        
        return new MarketSentiment(bullishPct, bearishPct, neutralPct, overall);
    }
    
    private String formatMarketScanResults(List<MarketScanResult> highConfidence,
                                          List<MarketScanResult> mediumConfidence,
                                          MarketSentiment sentiment, int totalScanned) {
        StringBuilder result = new StringBuilder();
        result.append("📈 *Market Scan Results* - Top Recommendations\n\n");
        
        // High confidence picks with enhanced intraday focus
        if (!highConfidence.isEmpty()) {
            result.append("*🔥 HIGH CONFIDENCE INTRADAY PICKS (85%+):*\n");
            for (int i = 0; i < Math.min(3, highConfidence.size()); i++) {
                MarketScanResult pick = highConfidence.get(i);
                BigDecimal gainPct = pick.target1.subtract(pick.currentPrice)
                    .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
                BigDecimal lossPct = pick.currentPrice.subtract(pick.stopLoss)
                    .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
                
                result.append(String.format("%d. **%s** (%.0f%% confidence) 🚀\n",
                    i + 1, pick.symbol, pick.confidence));
                result.append(String.format("   Entry: ₹%.0f → Target: ₹%.0f (+%.1f%%) | Stop: ₹%.0f (-%.1f%%)\n",
                    pick.entryPrice, pick.target1, gainPct, pick.stopLoss, lossPct));
                result.append(String.format("   ✅ Strategies: %s\n", 
                    String.join(" | ", pick.passedStrategies.subList(0, Math.min(4, pick.passedStrategies.size())))));
            }
            result.append("\n");
        }
        
        // Medium confidence picks
        if (!mediumConfidence.isEmpty()) {
            result.append("*⚡ MEDIUM CONFIDENCE PICKS (70-84%):*\n");
            for (int i = 0; i < Math.min(3, mediumConfidence.size()); i++) {
                MarketScanResult pick = mediumConfidence.get(i);
                BigDecimal gainPct = pick.target1.subtract(pick.currentPrice)
                    .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
                result.append(String.format("%d. **%s** (%.0f%% confidence) 📈\n",
                    i + 4, pick.symbol, pick.confidence));
                result.append(String.format("   Entry: ₹%.0f → Target: ₹%.0f (+%.1f%%) | Risk: Moderate\n",
                    pick.entryPrice, pick.target1, gainPct));
            }
            result.append("\n");
        }
        
        // Analysis summary
        result.append("*📊 Analysis Summary:*\n");
        result.append(String.format("• Scanned: %d Nifty stocks\n", totalScanned));
        result.append(String.format("• Bullish: %d%% | Bearish: %d%% | Neutral: %d%%\n",
            sentiment.bullish, sentiment.bearish, sentiment.neutral));
        result.append(String.format("• Market Sentiment: **%s** 📈\n\n", sentiment.overall));
        
        result.append("⏰ *INTRADAY TRADING WINDOW:*\n");
        java.time.LocalTime now = java.time.LocalTime.now();
        String marketStatus;
        if (now.isAfter(java.time.LocalTime.of(9, 15)) && now.isBefore(java.time.LocalTime.of(15, 30))) {
            marketStatus = "🟢 Market OPEN - Active Trading";
        } else if (now.isBefore(java.time.LocalTime.of(9, 15))) {
            marketStatus = "🟡 Pre-Market - Get Ready!";
        } else {
            marketStatus = "🔴 Market CLOSED - Plan for Tomorrow";
        }
        result.append(String.format("• Status: %s\n", marketStatus));
        result.append("• Hold Duration: 2-6 hours max\n");
        result.append("• Exit by: 3:15 PM (avoid last 15 mins)\n\n");
        result.append("💡 Use `/analyze SYMBOL` for detailed ML analysis!\n");
        result.append("⚠️ Trade at your own risk. Set stop losses always.");
        
        return result.toString();
    }
    
    /**
     * Format dynamic market scan results with market cap categorization
     */
    private String formatDynamicMarketScanResults(DynamicMarketScannerService.DynamicScanResult dynamicResult,
                                                 List<MarketScanResult> highConfidence,
                                                 List<MarketScanResult> mediumConfidence,
                                                 MarketSentiment sentiment, int totalScanned) {
        StringBuilder result = new StringBuilder();
        result.append("🎯 *Dynamic Market Scan* - Categorized Top Picks\n\n");
        
        // Price category breakdown
        result.append("*📊 PRICE CATEGORY ANALYSIS:*\n");
        for (DynamicMarketScannerService.MarketCapCategory category : DynamicMarketScannerService.MarketCapCategory.values()) {
            int categoryCount = dynamicResult.getCategoryPicks(category).size();
            result.append(String.format("• %s %s: %d stocks (%s)\n", 
                category.getEmoji(), category.getDisplayName(), categoryCount, category.getRange()));
        }
        result.append("\n");
        
        // Show picks by category with detailed information
        result.append("*🔥 HIGH CONFIDENCE PICKS (70%+):*\n\n");
        
        // Group high confidence picks by category
        Map<DynamicMarketScannerService.MarketCapCategory, List<MarketScanResult>> highConfidenceByCategory = 
            highConfidence.stream().collect(Collectors.groupingBy(r -> r.marketCapCategory));
        
        int globalIndex = 1;
        for (DynamicMarketScannerService.MarketCapCategory category : DynamicMarketScannerService.MarketCapCategory.values()) {
            List<MarketScanResult> categoryPicks = highConfidenceByCategory.getOrDefault(category, new ArrayList<>());
            if (!categoryPicks.isEmpty()) {
                result.append(String.format("**%s %s (%s):**\n", 
                    category.getEmoji(), category.getDisplayName().toUpperCase(), category.getRange()));
                
                for (MarketScanResult pick : categoryPicks.subList(0, Math.min(3, categoryPicks.size()))) {
                    BigDecimal gainPct = pick.target1.subtract(pick.currentPrice)
                        .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("100"));
                    BigDecimal lossPct = pick.currentPrice.subtract(pick.stopLoss)
                        .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("100"));
                    
                    result.append(String.format("%d. **%s** [%s %s] (%.0f%% confidence) 🚀\n",
                        globalIndex++, pick.symbol, category.getEmoji(), category.getDisplayName(), pick.confidence));
                    result.append(String.format("   Entry: ₹%.0f → Target: ₹%.0f (+%.1f%%) | Stop: ₹%.0f (-%.1f%%)\n",
                        pick.entryPrice, pick.target1, gainPct, pick.stopLoss, lossPct));
                    result.append(String.format("   ✅ Strategies: %s\n\n", 
                        String.join(" | ", pick.passedStrategies.subList(0, Math.min(4, pick.passedStrategies.size())))));
                }
            }
        }
        
        // Medium confidence picks by category
        if (!mediumConfidence.isEmpty()) {
            result.append("*⚡ MEDIUM CONFIDENCE PICKS (50-69%):*\n\n");
            
            Map<DynamicMarketScannerService.MarketCapCategory, List<MarketScanResult>> mediumConfidenceByCategory = 
                mediumConfidence.stream().collect(Collectors.groupingBy(r -> r.marketCapCategory));
                
            for (DynamicMarketScannerService.MarketCapCategory category : DynamicMarketScannerService.MarketCapCategory.values()) {
                List<MarketScanResult> categoryPicks = mediumConfidenceByCategory.getOrDefault(category, new ArrayList<>());
                if (!categoryPicks.isEmpty()) {
                    result.append(String.format("**%s %s:**\n", category.getEmoji(), category.getDisplayName().toUpperCase()));
                    
                    for (MarketScanResult pick : categoryPicks.subList(0, Math.min(2, categoryPicks.size()))) {
                        BigDecimal gainPct = pick.target1.subtract(pick.currentPrice)
                            .divide(pick.currentPrice, 3, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal("100"));
                        
                        result.append(String.format("• **%s** (%.0f%% confidence) 📈\n",
                            pick.symbol, pick.confidence));
                        result.append(String.format("  Entry: ₹%.0f → Target: ₹%.0f (+%.1f%%) | Risk: Moderate\n\n",
                            pick.entryPrice, pick.target1, gainPct));
                    }
                }
            }
        }
        
        // Market status and data source information
        result.append("*📈 MARKET STATUS & DATA SOURCE:*\n");
        java.time.LocalTime now = java.time.LocalTime.now();
        String marketStatusText;
        if (now.isAfter(java.time.LocalTime.of(9, 15)) && now.isBefore(java.time.LocalTime.of(15, 30))) {
            marketStatusText = "Open";
        } else if (now.isBefore(java.time.LocalTime.of(9, 15))) {
            marketStatusText = "Pre-Market";
        } else {
            marketStatusText = "Closed";
        }
        result.append(String.format("📈 Market Status: %s\n", marketStatusText));
        result.append("🔄 Data Source: Live NSE API + Smart Fallback\n");
        result.append("📊 Live Data Stats:\n");
        result.append("✅ NSE Success: Dynamic fetching\n");
        result.append("🟡 Yahoo Fallback: Smart switching\n");
        result.append("🔴 Mock Fallback: Emergency only\n\n");
        
        // Enhanced analysis summary with dynamic insights
        result.append("*📊 DYNAMIC ANALYSIS SUMMARY:*\n");
        result.append(String.format("• Total Scanned: %d live market stocks\n", dynamicResult.getTotalStocks()));
        result.append(String.format("• Analyzed: %d stocks passed filters\n", totalScanned));
        result.append(String.format("• Price Distribution: Giant→Large→Mid→Small→Tiny\n"));
        result.append(String.format("• Sentiment: Bullish %d%% | Bearish %d%% | Neutral %d%%\n",
            sentiment.bullish, sentiment.bearish, sentiment.neutral));
        result.append(String.format("• Overall Mood: **%s** 📈\n\n", sentiment.overall));
        
        // Enhanced intraday trading window
        result.append("⏰ *DYNAMIC INTRADAY WINDOW:*\n");
        String marketStatus;
        if (now.isAfter(java.time.LocalTime.of(9, 15)) && now.isBefore(java.time.LocalTime.of(15, 30))) {
            marketStatus = "🟢 Live Market Data - Active Trading";
            if (now.isBefore(java.time.LocalTime.of(11, 30))) {
                result.append("• Session: Morning Volatility (Best for momentum)\n");
            } else {
                result.append("• Session: Afternoon Consolidation (Best for reversals)\n");
            }
        } else if (now.isBefore(java.time.LocalTime.of(9, 15))) {
            marketStatus = "🟡 Pre-Market Analysis - Position Ready!";
        } else {
            marketStatus = "🔴 Post-Market Analysis - Plan Tomorrow";
        }
        result.append(String.format("• Status: %s\n", marketStatus));
        result.append("• Strategy: Dynamic market cap diversification\n");
        result.append("• Hold Duration: 2-6 hours max (exit by 3:15 PM)\n\n");
        
        result.append("🎯 *PRICE CATEGORY ADVANTAGE:*\n");
        result.append("• Giant Stocks (₹2000+): Premium quality, stable moves\n");
        result.append("• Medium Stocks (₹500-1000): Balanced growth potential\n");
        result.append("• Tiny/Small Stocks (< ₹500): High growth potential, higher risk\n");
        result.append("• Mixed Portfolio: Diversified price range for balanced risk\n\n");
        
        result.append("💡 Use `/analyze SYMBOL` for detailed ML analysis!\n");
        result.append("⚠️ Dynamic picks change with market. Set stop losses always.");
        
        return result.toString();
    }
    
    private String formatDetailedAnalysis(String symbol, ComprehensiveAnalysis analysis, StockData currentData) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("📊 *Deep Analysis: %s*\n\n", symbol));
        
        // Current market data
        result.append("*💰 Current Market Data:*\n");
        BigDecimal changePercent = currentData.getPercentChange() != null ? 
            currentData.getPercentChange() : BigDecimal.ZERO;
        String changeSymbol = changePercent.compareTo(BigDecimal.ZERO) >= 0 ? "↗️" : "↘️";
        result.append(String.format("Price: ₹%.2f (%+.1f%% %s)\n", 
            analysis.currentPrice, changePercent, changeSymbol));
        result.append(String.format("Volume: %s (%s Average)\n", 
            formatVolume(currentData.getVolume()), 
            currentData.getVolume() > 1000000 ? "Above" : "Below"));
        result.append("\n");
        
        // Trading recommendation with enhanced intraday focus
        result.append("*🎯 INTRADAY Trading Recommendation:*\n");
        result.append(String.format("**Action:** %s %s (Confidence: %.0f%%)\n", 
            analysis.recommendation.toString().replace("_", " "),
            getRecommendationEmoji(analysis.recommendation),
            analysis.confidence));
        result.append(String.format("**Entry Zone:** ₹%.2f - ₹%.2f\n", analysis.entryLow, analysis.entryHigh));
        
        // Calculate risk-reward ratios
        BigDecimal entryPrice = analysis.entryHigh;
        BigDecimal riskAmount = entryPrice.subtract(analysis.stopLoss);
        BigDecimal reward1 = analysis.target1.subtract(entryPrice);
        BigDecimal reward2 = analysis.target2.subtract(entryPrice);
        BigDecimal riskReward1 = riskAmount.compareTo(BigDecimal.ZERO) > 0 ? 
            reward1.divide(riskAmount, 1, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
        BigDecimal riskReward2 = riskAmount.compareTo(BigDecimal.ZERO) > 0 ? 
            reward2.divide(riskAmount, 1, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
        
        result.append(String.format("**Target 1:** ₹%.2f (+%.1f%%) [R:R 1:%.1f]\n", 
            analysis.target1, 
            analysis.target1.subtract(analysis.currentPrice).divide(analysis.currentPrice, 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100")),
            riskReward1));
        result.append(String.format("**Target 2:** ₹%.2f (+%.1f%%) [R:R 1:%.1f]\n", 
            analysis.target2,
            analysis.target2.subtract(analysis.currentPrice).divide(analysis.currentPrice, 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100")),
            riskReward2));
        result.append(String.format("**Stop Loss:** ₹%.2f (-%.1f%%)\n\n", 
            analysis.stopLoss,
            analysis.currentPrice.subtract(analysis.stopLoss).divide(analysis.currentPrice, 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"))));
        
        // Comprehensive Analysis Summary
        result.append("*🔍 Multi-Strategy Analysis:*\n");
        
        // Technical indicators
        result.append("**Technical Indicators:**\n");
        for (TechnicalAnalysisService.StrategyResult strategy : analysis.technical.getStrategies()) {
            String status = strategy.getSignal() != TechnicalAnalysisService.StrategyResult.StrategySignal.NEUTRAL ? "✅" : "❌";
            String signal = strategy.getSignal().toString().replace("_", " ");
            result.append(String.format("• %s: %s %s (%.0f%%)\n", 
                strategy.getName(), signal, status, strategy.getConfidence()));
        }
        
        // ML Analysis
        result.append("\n**🤖 ML Prediction:**\n");
        String mlDirection = analysis.ml.getDirection().toString().toLowerCase();
        String mlEmoji = analysis.ml.isBullish() ? "📈" : analysis.ml.isBearish() ? "📉" : "➡️";
        result.append(String.format("• Direction: %s %s (%.0f%% confidence)\n", 
            mlDirection.substring(0, 1).toUpperCase() + mlDirection.substring(1), 
            mlEmoji, analysis.ml.getConfidence()));
        result.append(String.format("• Target Price: ₹%.2f\n", analysis.ml.getTargetPrice()));
        
        // News Sentiment
        result.append("\n**📰 News Sentiment:**\n");
        String sentimentDirection = analysis.sentiment.isBullish() ? "Positive" : 
                                   analysis.sentiment.isBearish() ? "Negative" : "Neutral";
        String sentimentEmoji = analysis.sentiment.isBullish() ? "😊" : 
                              analysis.sentiment.isBearish() ? "😟" : "😐";
        result.append(String.format("• Market Mood: %s %s (%.0f%% confidence)\n", 
            sentimentDirection, sentimentEmoji, analysis.sentiment.getConfidence()));
        
        // Overall Strategy Performance
        result.append(String.format("\n**📊 Strategy Performance:**\n"));
        int totalStrategies = analysis.technical.getTotalStrategies() + 2; // +ML +Sentiment
        int passedStrategies = analysis.technical.getStrategiesPassed();
        if (analysis.ml.getConfidence().compareTo(new BigDecimal("70")) >= 0) passedStrategies++;
        if (analysis.sentiment.getConfidence().compareTo(new BigDecimal("60")) >= 0) passedStrategies++;
        
        result.append(String.format("• Success Rate: %d/%d strategies (%.0f%%)\n", 
            passedStrategies, totalStrategies, 
            (passedStrategies * 100.0) / totalStrategies));
        result.append(String.format("• Combined Confidence: %.0f%%\n\n", analysis.confidence));
        
        // Enhanced time horizon and risk with intraday focus
        result.append("*⏰ INTRADAY TIMING & RISK:*\n");
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        if (currentTime.isAfter(java.time.LocalTime.of(9, 15)) && currentTime.isBefore(java.time.LocalTime.of(15, 30))) {
            result.append("• Market Status: 🟢 LIVE TRADING\n");
            if (currentTime.isBefore(java.time.LocalTime.of(11, 00))) {
                result.append("• Session: Morning (High Volatility)\n");
                result.append("• Expected Move: 1-3 hours\n");
            } else if (currentTime.isBefore(java.time.LocalTime.of(14, 00))) {
                result.append("• Session: Mid-day (Moderate Activity)\n");
                result.append("• Expected Move: 2-4 hours\n");
            } else {
                result.append("• Session: Afternoon (Exit Preparation)\n");
                result.append("• Expected Move: By 3:15 PM\n");
            }
        } else {
            result.append("• Market Status: 🔴 CLOSED\n");
            result.append("• Next Session: 9:15 AM Tomorrow\n");
            result.append("• Expected Move: First 2-4 hours\n");
        }
        
        result.append(String.format("• Risk Level: **%s** ⚠️\n", analysis.riskAssessment));
        result.append("• Strategy: Intraday scalping with ML targets\n");
        result.append("• Exit Rule: Target hit OR 3:15 PM (whichever first)\n\n");
        
        result.append("🤖 *AI-Powered Analysis Complete*\n");
        result.append("Updated: Just now | ML Models: Active\n");
        result.append("⚠️ **Remember: Set stop loss ALWAYS!**");
        
        return result.toString();
    }
    
    private String getRecommendationEmoji(StockAnalysis.Recommendation recommendation) {
        return switch (recommendation) {
            case STRONG_BUY -> "🚀";
            case BUY -> "📈";
            case STRONG_SELL -> "🔻";
            case SELL -> "📉";
            default -> "➡️";
        };
    }
    
    private String formatVolume(Long volume) {
        if (volume == null) return "N/A";
        if (volume >= 10_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 100_000) {
            return String.format("%.0fK", volume / 1_000.0);
        } else {
            return volume.toString();
        }
    }
    
    // Data classes
    private static class MarketScanResult {
        final String symbol;
        final StockAnalysis.Recommendation recommendation;
        final BigDecimal confidence;
        final BigDecimal currentPrice;
        final BigDecimal entryPrice;
        final BigDecimal target1;
        final BigDecimal stopLoss;
        final List<String> passedStrategies;
        DynamicMarketScannerService.MarketCapCategory marketCapCategory; // Added for dynamic categorization
        
        MarketScanResult(String symbol, StockAnalysis.Recommendation recommendation, BigDecimal confidence,
                        BigDecimal currentPrice, BigDecimal entryPrice, BigDecimal target1,
                        BigDecimal stopLoss, List<String> passedStrategies) {
            this.symbol = symbol;
            this.recommendation = recommendation;
            this.confidence = confidence;
            this.currentPrice = currentPrice;
            this.entryPrice = entryPrice;
            this.target1 = target1;
            this.stopLoss = stopLoss;
            this.passedStrategies = passedStrategies;
        }
    }
    
    private static class MarketSentiment {
        final int bullish;
        final int bearish;
        final int neutral;
        final String overall;
        
        MarketSentiment(int bullish, int bearish, int neutral, String overall) {
            this.bullish = bullish;
            this.bearish = bearish;
            this.neutral = neutral;
            this.overall = overall;
        }
    }
    
    private static class ComprehensiveAnalysis {
        final StockAnalysis.Recommendation recommendation;
        final BigDecimal confidence;
        final BigDecimal currentPrice;
        final BigDecimal entryLow;
        final BigDecimal entryHigh;
        final BigDecimal target1;
        final BigDecimal target2;
        final BigDecimal stopLoss;
        final TechnicalAnalysisService.TechnicalAnalysisResult technical;
        final NewsSentimentService.NewsSentimentResult sentiment;
        final MLPredictionService.MLPredictionResult ml;
        final String riskAssessment;
        
        ComprehensiveAnalysis(StockAnalysis.Recommendation recommendation, BigDecimal confidence,
                            BigDecimal currentPrice, BigDecimal entryLow, BigDecimal entryHigh,
                            BigDecimal target1, BigDecimal target2, BigDecimal stopLoss,
                            TechnicalAnalysisService.TechnicalAnalysisResult technical,
                            NewsSentimentService.NewsSentimentResult sentiment,
                            MLPredictionService.MLPredictionResult ml, String riskAssessment) {
            this.recommendation = recommendation;
            this.confidence = confidence;
            this.currentPrice = currentPrice;
            this.entryLow = entryLow;
            this.entryHigh = entryHigh;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.technical = technical;
            this.sentiment = sentiment;
            this.ml = ml;
            this.riskAssessment = riskAssessment;
        }
    }
}