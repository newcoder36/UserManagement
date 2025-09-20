package com.nsebot.analysis.ml;

import com.nsebot.dto.StockData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Machine Learning Prediction Service
 * 
 * Provides ML-based stock price predictions using various algorithms:
 * - Linear Regression for trend analysis
 * - Moving Average Convergence for momentum prediction
 * - Feature engineering from technical indicators
 * - Price direction classification
 * 
 * Note: This is a simplified implementation. In production, this would
 * integrate with TensorFlow models trained on historical data.
 */
@Service
public class MLPredictionService {
    
    private static final Logger logger = LoggerFactory.getLogger(MLPredictionService.class);
    
    private static final int MIN_DATA_POINTS = 20;
    private static final int FEATURE_WINDOW = 10;
    
    /**
     * Predict price movement for next period
     */
    @Cacheable(value = "mlPrediction", key = "#symbol + '_' + #historicalData.size()", 
               unless = "#result.confidence.compareTo(new java.math.BigDecimal('40')) < 0")
    public MLPredictionResult predictPriceMovement(String symbol, List<StockData> historicalData) {
        logger.info("Generating ML prediction for symbol: {}", symbol);
        
        if (historicalData == null || historicalData.size() < MIN_DATA_POINTS) {
            return new MLPredictionResult(symbol, PredictionDirection.NEUTRAL, 
                    BigDecimal.ZERO, new BigDecimal("0"), 
                    "Insufficient historical data for ML prediction");
        }
        
        try {
            // Sort data chronologically
            List<StockData> sortedData = new ArrayList<>(historicalData);
            sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
            
            // Extract features for ML model
            MLFeatures features = extractFeatures(sortedData);
            
            // Generate prediction using simplified model
            MLPrediction prediction = generatePrediction(features, sortedData);
            
            // Calculate confidence based on model consistency
            BigDecimal confidence = calculatePredictionConfidence(features, sortedData);
            
            String interpretation = generatePredictionInterpretation(symbol, prediction, confidence);
            
            return new MLPredictionResult(symbol, prediction.direction, 
                    prediction.targetPrice, confidence, interpretation);
            
        } catch (Exception e) {
            logger.error("Error generating ML prediction for symbol: {}", symbol, e);
            return new MLPredictionResult(symbol, PredictionDirection.NEUTRAL, 
                    BigDecimal.ZERO, new BigDecimal("0"), 
                    "Error in ML prediction: " + e.getMessage());
        }
    }
    
    /**
     * Extract features for ML model
     */
    private MLFeatures extractFeatures(List<StockData> data) {
        if (data.size() < FEATURE_WINDOW) {
            return new MLFeatures();
        }
        
        MLFeatures features = new MLFeatures();
        
        // Get recent data window
        List<StockData> recentData = data.subList(data.size() - FEATURE_WINDOW, data.size());
        
        // Price-based features
        features.currentPrice = getCurrentPrice(data);
        features.priceChange = calculatePriceChange(recentData);
        features.volatility = calculateVolatility(recentData);
        features.momentum = calculateMomentum(recentData);
        
        // Volume-based features
        features.volumeTrend = calculateVolumeTrend(recentData);
        features.relativeVolume = calculateRelativeVolume(recentData);
        
        // Technical indicator features
        features.rsiSignal = calculateSimpleRSI(recentData);
        features.macdSignal = calculateSimpleMacd(recentData);
        features.movingAverageSignal = calculateMovingAverageSignal(recentData);
        
        return features;
    }
    
    /**
     * Generate prediction using simplified ML model
     */
    private MLPrediction generatePrediction(MLFeatures features, List<StockData> data) {
        // Simplified linear regression model
        BigDecimal currentPrice = features.currentPrice;
        
        // Weight different factors
        BigDecimal trendWeight = new BigDecimal("0.3");
        BigDecimal momentumWeight = new BigDecimal("0.25");
        BigDecimal volumeWeight = new BigDecimal("0.15");
        BigDecimal technicalWeight = new BigDecimal("0.3");
        
        // Calculate weighted prediction
        BigDecimal trendScore = features.priceChange.multiply(trendWeight);
        BigDecimal momentumScore = features.momentum.multiply(momentumWeight);
        BigDecimal volumeScore = features.volumeTrend.multiply(volumeWeight);
        BigDecimal technicalScore = calculateTechnicalScore(features).multiply(technicalWeight);
        
        BigDecimal totalScore = trendScore.add(momentumScore).add(volumeScore).add(technicalScore);
        
        // Determine direction and target price
        PredictionDirection direction;
        BigDecimal targetPrice;
        
        if (totalScore.compareTo(new BigDecimal("0.05")) > 0) {
            direction = PredictionDirection.BULLISH;
            targetPrice = currentPrice.multiply(BigDecimal.ONE.add(totalScore));
        } else if (totalScore.compareTo(new BigDecimal("-0.05")) < 0) {
            direction = PredictionDirection.BEARISH;
            targetPrice = currentPrice.multiply(BigDecimal.ONE.add(totalScore));
        } else {
            direction = PredictionDirection.NEUTRAL;
            targetPrice = currentPrice;
        }
        
        return new MLPrediction(direction, targetPrice);
    }
    
    /**
     * Calculate prediction confidence
     */
    private BigDecimal calculatePredictionConfidence(MLFeatures features, List<StockData> data) {
        // Base confidence on data quality and consistency
        BigDecimal baseConfidence = new BigDecimal("50");
        
        // Adjust based on volatility (lower volatility = higher confidence)
        if (features.volatility.compareTo(new BigDecimal("0.02")) < 0) {
            baseConfidence = baseConfidence.add(new BigDecimal("15"));
        } else if (features.volatility.compareTo(new BigDecimal("0.05")) > 0) {
            baseConfidence = baseConfidence.subtract(new BigDecimal("10"));
        }
        
        // Adjust based on volume consistency
        if (features.relativeVolume.compareTo(new BigDecimal("0.5")) > 0 && 
            features.relativeVolume.compareTo(new BigDecimal("2.0")) < 0) {
            baseConfidence = baseConfidence.add(new BigDecimal("10"));
        }
        
        // Adjust based on technical indicator alignment
        int alignedIndicators = 0;
        if (features.rsiSignal.abs().compareTo(new BigDecimal("0.3")) > 0) alignedIndicators++;
        if (features.macdSignal.abs().compareTo(new BigDecimal("0.3")) > 0) alignedIndicators++;
        if (features.movingAverageSignal.abs().compareTo(new BigDecimal("0.3")) > 0) alignedIndicators++;
        
        baseConfidence = baseConfidence.add(new BigDecimal(alignedIndicators * 8));
        
        // Cap confidence at 85% for ML predictions
        return baseConfidence.min(new BigDecimal("85"));
    }
    
    /**
     * Calculate technical indicators score
     */
    private BigDecimal calculateTechnicalScore(MLFeatures features) {
        return features.rsiSignal
                .add(features.macdSignal)
                .add(features.movingAverageSignal)
                .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
    }
    
    // Feature calculation methods
    
    private BigDecimal getCurrentPrice(List<StockData> data) {
        return data.get(data.size() - 1).getLastPrice();
    }
    
    private BigDecimal calculatePriceChange(List<StockData> data) {
        if (data.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal current = data.get(data.size() - 1).getLastPrice();
        BigDecimal previous = data.get(0).getLastPrice();
        
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateVolatility(List<StockData> data) {
        if (data.size() < 2) return BigDecimal.ZERO;
        
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            BigDecimal current = data.get(i).getLastPrice();
            BigDecimal previous = data.get(i - 1).getLastPrice();
            BigDecimal returnValue = current.subtract(previous).divide(previous, 6, RoundingMode.HALF_UP);
            returns.add(returnValue);
        }
        
        // Calculate standard deviation
        BigDecimal mean = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(returns.size()), 6, RoundingMode.HALF_UP);
        
        BigDecimal variance = returns.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(returns.size()), 6, RoundingMode.HALF_UP);
        
        return sqrt(variance);
    }
    
    private BigDecimal calculateMomentum(List<StockData> data) {
        if (data.size() < 5) return BigDecimal.ZERO;
        
        // Simple momentum: (current - 5 periods ago) / 5 periods ago
        BigDecimal current = data.get(data.size() - 1).getLastPrice();
        BigDecimal past = data.get(Math.max(0, data.size() - 5)).getLastPrice();
        
        return current.subtract(past).divide(past, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateVolumeTrend(List<StockData> data) {
        if (data.size() < 5) return BigDecimal.ZERO;
        
        // Compare recent average volume to earlier average volume
        long recentVolume = data.subList(data.size() - 3, data.size()).stream()
                .mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0)
                .sum() / 3;
        
        long pastVolume = data.subList(0, Math.min(3, data.size())).stream()
                .mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0)
                .sum() / Math.min(3, data.size());
        
        if (pastVolume == 0) return BigDecimal.ZERO;
        
        return new BigDecimal(recentVolume - pastVolume).divide(new BigDecimal(pastVolume), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateRelativeVolume(List<StockData> data) {
        if (data.isEmpty()) return BigDecimal.ZERO;
        
        long currentVolume = data.get(data.size() - 1).getVolume() != null ? 
                data.get(data.size() - 1).getVolume() : 0;
        
        double avgVolume = data.stream()
                .mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0)
                .average()
                .orElse(1);
        
        return new BigDecimal(currentVolume).divide(new BigDecimal(avgVolume), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateSimpleRSI(List<StockData> data) {
        // Simplified RSI calculation returning normalized signal (-1 to 1)
        if (data.size() < 5) return BigDecimal.ZERO;
        
        int gains = 0, losses = 0;
        for (int i = 1; i < data.size(); i++) {
            BigDecimal change = data.get(i).getLastPrice().subtract(data.get(i - 1).getLastPrice());
            if (change.compareTo(BigDecimal.ZERO) > 0) gains++;
            else if (change.compareTo(BigDecimal.ZERO) < 0) losses++;
        }
        
        if (gains + losses == 0) return BigDecimal.ZERO;
        
        double rsi = (double) gains / (gains + losses);
        
        // Normalize to -1 to 1 scale
        return new BigDecimal(2 * rsi - 1).setScale(4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateSimpleMacd(List<StockData> data) {
        // Simplified MACD signal (-1 to 1)
        if (data.size() < 10) return BigDecimal.ZERO;
        
        // Fast EMA (5 periods) - Slow EMA (10 periods)
        BigDecimal fastEMA = calculateSimpleEMA(data, 5);
        BigDecimal slowEMA = calculateSimpleEMA(data, 10);
        
        BigDecimal macd = fastEMA.subtract(slowEMA);
        
        // Normalize relative to current price
        BigDecimal currentPrice = getCurrentPrice(data);
        return macd.divide(currentPrice, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateMovingAverageSignal(List<StockData> data) {
        // Price vs 10-period SMA signal
        if (data.size() < 10) return BigDecimal.ZERO;
        
        BigDecimal sma = calculateSimpleSMA(data, 10);
        BigDecimal currentPrice = getCurrentPrice(data);
        
        return currentPrice.subtract(sma).divide(sma, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateSimpleEMA(List<StockData> data, int periods) {
        if (data.size() < periods) return getCurrentPrice(data);
        
        BigDecimal multiplier = new BigDecimal("2").divide(new BigDecimal(periods + 1), 4, RoundingMode.HALF_UP);
        BigDecimal ema = data.get(data.size() - periods).getLastPrice();
        
        for (int i = data.size() - periods + 1; i < data.size(); i++) {
            ema = data.get(i).getLastPrice().multiply(multiplier)
                    .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema;
    }
    
    private BigDecimal calculateSimpleSMA(List<StockData> data, int periods) {
        if (data.size() < periods) return getCurrentPrice(data);
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = data.size() - periods; i < data.size(); i++) {
            sum = sum.add(data.get(i).getLastPrice());
        }
        
        return sum.divide(new BigDecimal(periods), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        
        BigDecimal x = value.divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        for (int i = 0; i < 10; i++) {
            x = x.add(value.divide(x, 6, RoundingMode.HALF_UP))
                 .divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        }
        return x;
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generatePredictionInterpretation(String symbol, MLPrediction prediction, BigDecimal confidence) {
        String directionText = switch (prediction.direction) {
            case BULLISH -> "Bullish";
            case BEARISH -> "Bearish";
            case NEUTRAL -> "Neutral";
        };
        
        return String.format("ML Prediction: %s direction (%.0f%% confidence) - Target: ₹%.2f", 
                directionText, confidence, prediction.targetPrice);
    }
    
    // Inner classes
    
    private static class MLFeatures {
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal priceChange = BigDecimal.ZERO;
        BigDecimal volatility = BigDecimal.ZERO;
        BigDecimal momentum = BigDecimal.ZERO;
        BigDecimal volumeTrend = BigDecimal.ZERO;
        BigDecimal relativeVolume = BigDecimal.ZERO;
        BigDecimal rsiSignal = BigDecimal.ZERO;
        BigDecimal macdSignal = BigDecimal.ZERO;
        BigDecimal movingAverageSignal = BigDecimal.ZERO;
    }
    
    private static class MLPrediction {
        final PredictionDirection direction;
        final BigDecimal targetPrice;
        
        MLPrediction(PredictionDirection direction, BigDecimal targetPrice) {
            this.direction = direction;
            this.targetPrice = targetPrice;
        }
    }
    
    /**
     * ML Prediction Result
     */
    public static class MLPredictionResult {
        private final String symbol;
        private final PredictionDirection direction;
        private final BigDecimal targetPrice;
        private final BigDecimal confidence;
        private final String interpretation;
        
        public MLPredictionResult(String symbol, PredictionDirection direction,
                                BigDecimal targetPrice, BigDecimal confidence, String interpretation) {
            this.symbol = symbol;
            this.direction = direction;
            this.targetPrice = targetPrice;
            this.confidence = confidence;
            this.interpretation = interpretation;
        }
        
        public String getSymbol() { return symbol; }
        public PredictionDirection getDirection() { return direction; }
        public BigDecimal getTargetPrice() { return targetPrice; }
        public BigDecimal getConfidence() { return confidence; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() { return direction == PredictionDirection.BULLISH; }
        public boolean isBearish() { return direction == PredictionDirection.BEARISH; }
        public boolean isNeutral() { return direction == PredictionDirection.NEUTRAL; }
        
        @Override
        public String toString() {
            return String.format("MLPrediction(%s, %s, ₹%.2f, %.0f%%)", 
                               symbol, direction, targetPrice, confidence);
        }
    }
    
    /**
     * Prediction direction enumeration
     */
    public enum PredictionDirection {
        BULLISH,   // Expecting price increase
        BEARISH,   // Expecting price decrease
        NEUTRAL    // No clear direction
    }
}