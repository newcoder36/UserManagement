package com.nsebot.analysis;

import com.nsebot.analysis.indicators.*;
import com.nsebot.dto.StockData;
import com.nsebot.entity.StockAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;

/**
 * Technical Analysis Service that coordinates all indicators and provides comprehensive analysis
 */
@Service
public class TechnicalAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(TechnicalAnalysisService.class);
    
    @Autowired
    private RSIIndicator rsiIndicator;
    
    @Autowired
    private MACDIndicator macdIndicator;
    
    @Autowired
    private BollingerBandsIndicator bollingerBandsIndicator;
    
    @Autowired
    private MovingAverageIndicator movingAverageIndicator;
    
    @Autowired
    private VolumeIndicator volumeIndicator;
    
    /**
     * Perform comprehensive technical analysis on stock data
     */
    @Cacheable(value = "technicalAnalysis", key = "#symbol + '_' + #historicalData.size()", 
               unless = "#result.confidence.compareTo(new java.math.BigDecimal('30')) < 0")
    public TechnicalAnalysisResult performTechnicalAnalysis(String symbol, List<StockData> historicalData) {
        logger.info("Performing technical analysis for symbol: {}", symbol);
        
        if (historicalData == null || historicalData.isEmpty()) {
            return new TechnicalAnalysisResult(symbol, 
                    StockAnalysis.Recommendation.HOLD, 
                    BigDecimal.ZERO, 
                    new ArrayList<>(), 
                    "No historical data available for analysis");
        }
        
        try {
            List<StrategyResult> strategies = new ArrayList<>();
            
            // 1. RSI Analysis
            RSIIndicator.RSIResult rsiResult = rsiIndicator.calculateRSI(historicalData);
            strategies.add(evaluateRSIStrategy(rsiResult));
            
            // 2. MACD Analysis
            MACDIndicator.MACDResult macdResult = macdIndicator.calculateMACD(historicalData);
            strategies.add(evaluateMACDStrategy(macdResult));
            
            // 3. Bollinger Bands Analysis
            BollingerBandsIndicator.BollingerBandsResult bbResult = 
                    bollingerBandsIndicator.calculateBollingerBands(historicalData);
            strategies.add(evaluateBollingerBandsStrategy(bbResult));
            
            // 4. Moving Average Analysis
            MovingAverageIndicator.MovingAverageResult sma20 = 
                    movingAverageIndicator.calculateSMA(historicalData, 20);
            MovingAverageIndicator.MovingAverageResult sma50 = 
                    movingAverageIndicator.calculateSMA(historicalData, 50);
            strategies.add(evaluateMovingAverageStrategy(sma20, sma50, getCurrentPrice(historicalData)));
            
            // 5. Volume Analysis
            VolumeIndicator.VolumeAnalysisResult volumeResult = 
                    volumeIndicator.analyzeVolume(historicalData);
            strategies.add(evaluateVolumeStrategy(volumeResult));
            
            // Calculate overall recommendation and confidence
            OverallAnalysis overall = calculateOverallAnalysis(strategies);
            
            String analysisNotes = generateAnalysisNotes(strategies);
            
            return new TechnicalAnalysisResult(symbol, overall.recommendation, 
                    overall.confidence, strategies, analysisNotes);
            
        } catch (Exception e) {
            logger.error("Error performing technical analysis for symbol: {}", symbol, e);
            return new TechnicalAnalysisResult(symbol, 
                    StockAnalysis.Recommendation.HOLD, 
                    BigDecimal.ZERO, 
                    new ArrayList<>(), 
                    "Error performing technical analysis: " + e.getMessage());
        }
    }
    
    /**
     * Evaluate RSI strategy
     */
    private StrategyResult evaluateRSIStrategy(RSIIndicator.RSIResult rsiResult) {
        StrategyResult.StrategySignal signal = StrategyResult.StrategySignal.NEUTRAL;
        BigDecimal confidence = new BigDecimal("50");
        
        if (rsiResult.isBullish()) {
            signal = StrategyResult.StrategySignal.BUY;
            confidence = new BigDecimal("75");
        } else if (rsiResult.isBearish()) {
            signal = StrategyResult.StrategySignal.SELL;
            confidence = new BigDecimal("75");
        }
        
        return new StrategyResult("RSI", signal, confidence, rsiResult.getInterpretation());
    }
    
    /**
     * Evaluate MACD strategy
     */
    private StrategyResult evaluateMACDStrategy(MACDIndicator.MACDResult macdResult) {
        StrategyResult.StrategySignal signal = StrategyResult.StrategySignal.NEUTRAL;
        BigDecimal confidence = new BigDecimal("50");
        
        if (macdResult.isBullish()) {
            signal = StrategyResult.StrategySignal.BUY;
            confidence = macdResult.getSignal() == MACDIndicator.MACDSignal.BULLISH_CROSSOVER 
                    ? new BigDecimal("85") : new BigDecimal("70");
        } else if (macdResult.isBearish()) {
            signal = StrategyResult.StrategySignal.SELL;
            confidence = macdResult.getSignal() == MACDIndicator.MACDSignal.BEARISH_CROSSOVER 
                    ? new BigDecimal("85") : new BigDecimal("70");
        }
        
        return new StrategyResult("MACD", signal, confidence, macdResult.getInterpretation());
    }
    
    /**
     * Evaluate Bollinger Bands strategy
     */
    private StrategyResult evaluateBollingerBandsStrategy(BollingerBandsIndicator.BollingerBandsResult bbResult) {
        StrategyResult.StrategySignal signal = StrategyResult.StrategySignal.NEUTRAL;
        BigDecimal confidence = new BigDecimal("50");
        
        if (bbResult.isBullish()) {
            signal = StrategyResult.StrategySignal.BUY;
            confidence = bbResult.getSignal() == BollingerBandsIndicator.BollingerBandsSignal.OVERSOLD 
                    ? new BigDecimal("80") : new BigDecimal("65");
        } else if (bbResult.isBearish()) {
            signal = StrategyResult.StrategySignal.SELL;
            confidence = bbResult.getSignal() == BollingerBandsIndicator.BollingerBandsSignal.OVERBOUGHT 
                    ? new BigDecimal("80") : new BigDecimal("65");
        }
        
        return new StrategyResult("Bollinger Bands", signal, confidence, bbResult.getInterpretation());
    }
    
    /**
     * Evaluate Moving Average strategy
     */
    private StrategyResult evaluateMovingAverageStrategy(MovingAverageIndicator.MovingAverageResult sma20,
                                                       MovingAverageIndicator.MovingAverageResult sma50,
                                                       BigDecimal currentPrice) {
        StrategyResult.StrategySignal signal = StrategyResult.StrategySignal.NEUTRAL;
        BigDecimal confidence = new BigDecimal("50");
        
        if (currentPrice != null && sma20.getValue() != null && sma50.getValue() != null) {
            boolean priceAboveSMA20 = currentPrice.compareTo(sma20.getValue()) > 0;
            boolean priceAboveSMA50 = currentPrice.compareTo(sma50.getValue()) > 0;
            boolean sma20AboveSMA50 = sma20.getValue().compareTo(sma50.getValue()) > 0;
            
            if (priceAboveSMA20 && priceAboveSMA50 && sma20AboveSMA50) {
                signal = StrategyResult.StrategySignal.BUY;
                confidence = new BigDecimal("80");
            } else if (!priceAboveSMA20 && !priceAboveSMA50 && !sma20AboveSMA50) {
                signal = StrategyResult.StrategySignal.SELL;
                confidence = new BigDecimal("80");
            } else if (priceAboveSMA20 && sma20AboveSMA50) {
                signal = StrategyResult.StrategySignal.BUY;
                confidence = new BigDecimal("65");
            } else if (!priceAboveSMA20 && !sma20AboveSMA50) {
                signal = StrategyResult.StrategySignal.SELL;
                confidence = new BigDecimal("65");
            }
        }
        
        String interpretation = String.format("SMA(20): %.2f, SMA(50): %.2f, Price: %.2f", 
                sma20.getValue(), sma50.getValue(), currentPrice);
        
        return new StrategyResult("Moving Average", signal, confidence, interpretation);
    }
    
    /**
     * Evaluate Volume strategy
     */
    private StrategyResult evaluateVolumeStrategy(VolumeIndicator.VolumeAnalysisResult volumeResult) {
        StrategyResult.StrategySignal signal = StrategyResult.StrategySignal.NEUTRAL;
        BigDecimal confidence = new BigDecimal("50");
        
        if (volumeResult.isBullish()) {
            signal = StrategyResult.StrategySignal.BUY;
            confidence = volumeResult.getSignal() == VolumeIndicator.VolumeSignal.STRONG_BULLISH 
                    ? new BigDecimal("75") : new BigDecimal("60");
        } else if (volumeResult.isBearish()) {
            signal = StrategyResult.StrategySignal.SELL;
            confidence = volumeResult.getSignal() == VolumeIndicator.VolumeSignal.STRONG_BEARISH 
                    ? new BigDecimal("75") : new BigDecimal("60");
        }
        
        return new StrategyResult("Volume Analysis", signal, confidence, volumeResult.getInterpretation());
    }
    
    /**
     * Calculate overall analysis from individual strategies
     */
    private OverallAnalysis calculateOverallAnalysis(List<StrategyResult> strategies) {
        if (strategies.isEmpty()) {
            return new OverallAnalysis(StockAnalysis.Recommendation.HOLD, BigDecimal.ZERO);
        }
        
        BigDecimal buyScore = BigDecimal.ZERO;
        BigDecimal sellScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (StrategyResult strategy : strategies) {
            BigDecimal weight = strategy.getConfidence().divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalWeight = totalWeight.add(weight);
            
            switch (strategy.getSignal()) {
                case BUY -> buyScore = buyScore.add(weight);
                case SELL -> sellScore = sellScore.add(weight);
                case NEUTRAL -> {
                    // Neutral strategies don't contribute to buy/sell scores
                }
            }
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return new OverallAnalysis(StockAnalysis.Recommendation.HOLD, BigDecimal.ZERO);
        }
        
        BigDecimal buyPercentage = buyScore.divide(totalWeight, 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        BigDecimal sellPercentage = sellScore.divide(totalWeight, 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        // Determine overall recommendation
        StockAnalysis.Recommendation recommendation;
        BigDecimal confidence;
        
        if (buyPercentage.compareTo(sellPercentage) > 0) {
            recommendation = buyPercentage.compareTo(new BigDecimal("70")) >= 0 
                    ? StockAnalysis.Recommendation.STRONG_BUY 
                    : StockAnalysis.Recommendation.BUY;
            confidence = buyPercentage;
        } else if (sellPercentage.compareTo(buyPercentage) > 0) {
            recommendation = sellPercentage.compareTo(new BigDecimal("70")) >= 0 
                    ? StockAnalysis.Recommendation.STRONG_SELL 
                    : StockAnalysis.Recommendation.SELL;
            confidence = sellPercentage;
        } else {
            recommendation = StockAnalysis.Recommendation.HOLD;
            confidence = new BigDecimal("50");
        }
        
        return new OverallAnalysis(recommendation, confidence);
    }
    
    /**
     * Generate analysis notes summarizing all strategies
     */
    private String generateAnalysisNotes(List<StrategyResult> strategies) {
        StringBuilder notes = new StringBuilder();
        notes.append("Technical Analysis Summary:\n");
        
        for (StrategyResult strategy : strategies) {
            notes.append(String.format("• %s: %s (%.0f%% confidence)\n", 
                    strategy.getName(), 
                    strategy.getSignal().toString().replace("_", " "), 
                    strategy.getConfidence()));
        }
        
        return notes.toString();
    }
    
    /**
     * Get current price from historical data
     */
    private BigDecimal getCurrentPrice(List<StockData> historicalData) {
        if (historicalData.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return historicalData.get(historicalData.size() - 1).getLastPrice();
    }
    
    /**
     * Technical Analysis Result
     */
    public static class TechnicalAnalysisResult {
        private final String symbol;
        private final StockAnalysis.Recommendation recommendation;
        private final BigDecimal confidence;
        private final List<StrategyResult> strategies;
        private final String analysisNotes;
        
        public TechnicalAnalysisResult(String symbol, StockAnalysis.Recommendation recommendation,
                                     BigDecimal confidence, List<StrategyResult> strategies,
                                     String analysisNotes) {
            this.symbol = symbol;
            this.recommendation = recommendation;
            this.confidence = confidence;
            this.strategies = strategies;
            this.analysisNotes = analysisNotes;
        }
        
        public String getSymbol() { return symbol; }
        public StockAnalysis.Recommendation getRecommendation() { return recommendation; }
        public BigDecimal getConfidence() { return confidence; }
        public List<StrategyResult> getStrategies() { return strategies; }
        public String getAnalysisNotes() { return analysisNotes; }
        
        public List<String> getPassedStrategies() {
            return strategies.stream()
                    .filter(s -> s.getSignal() != StrategyResult.StrategySignal.NEUTRAL)
                    .map(StrategyResult::getName)
                    .toList();
        }
        
        public int getStrategiesPassed() {
            return (int) strategies.stream()
                    .filter(s -> s.getSignal() != StrategyResult.StrategySignal.NEUTRAL)
                    .count();
        }
        
        public int getTotalStrategies() {
            return strategies.size();
        }
    }
    
    /**
     * Individual Strategy Result
     */
    public static class StrategyResult {
        private final String name;
        private final StrategySignal signal;
        private final BigDecimal confidence;
        private final String interpretation;
        
        public StrategyResult(String name, StrategySignal signal, BigDecimal confidence, String interpretation) {
            this.name = name;
            this.signal = signal;
            this.confidence = confidence;
            this.interpretation = interpretation;
        }
        
        public String getName() { return name; }
        public StrategySignal getSignal() { return signal; }
        public BigDecimal getConfidence() { return confidence; }
        public String getInterpretation() { return interpretation; }
        
        public enum StrategySignal {
            BUY, SELL, NEUTRAL
        }
    }
    
    /**
     * Overall Analysis result
     */
    private static class OverallAnalysis {
        private final StockAnalysis.Recommendation recommendation;
        private final BigDecimal confidence;
        
        public OverallAnalysis(StockAnalysis.Recommendation recommendation, BigDecimal confidence) {
            this.recommendation = recommendation;
            this.confidence = confidence;
        }
    }
}