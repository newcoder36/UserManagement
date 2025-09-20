package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bollinger Bands Technical Indicator
 * 
 * Bollinger Bands consist of three lines:
 * - Middle Band: Simple Moving Average (typically 20-period)
 * - Upper Band: Middle Band + (2 × Standard Deviation)
 * - Lower Band: Middle Band - (2 × Standard Deviation)
 * 
 * Trading signals:
 * - Price near Upper Band: Overbought condition (potential sell)
 * - Price near Lower Band: Oversold condition (potential buy)
 * - Band squeeze: Low volatility (potential breakout)
 * - Band expansion: High volatility (trending market)
 */
@Component
public class BollingerBandsIndicator {
    
    private static final int DEFAULT_PERIOD = 20;
    private static final BigDecimal DEFAULT_STD_DEV_MULTIPLIER = new BigDecimal("2.0");
    
    /**
     * Calculate Bollinger Bands with default parameters (20, 2.0)
     */
    public BollingerBandsResult calculateBollingerBands(List<StockData> stockData) {
        return calculateBollingerBands(stockData, DEFAULT_PERIOD, DEFAULT_STD_DEV_MULTIPLIER);
    }
    
    /**
     * Calculate Bollinger Bands with custom parameters
     */
    public BollingerBandsResult calculateBollingerBands(List<StockData> stockData, 
                                                       int period, BigDecimal stdDevMultiplier) {
        if (stockData == null || stockData.size() < period) {
            return new BollingerBandsResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BollingerBandsSignal.NEUTRAL, "Insufficient data for Bollinger Bands calculation");
        }
        
        // Sort by timestamp
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Extract prices for the last 'period' data points
        List<BigDecimal> prices = new ArrayList<>();
        int startIndex = Math.max(0, sortedData.size() - period);
        
        for (int i = startIndex; i < sortedData.size(); i++) {
            BigDecimal price = sortedData.get(i).getLastPrice();
            if (price != null) {
                prices.add(price);
            }
        }
        
        if (prices.size() < period) {
            return new BollingerBandsResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BollingerBandsSignal.NEUTRAL, "Insufficient valid price data for Bollinger Bands");
        }
        
        // Calculate Middle Band (SMA)
        BigDecimal middleBand = calculateSMA(prices);
        
        // Calculate Standard Deviation
        BigDecimal standardDeviation = calculateStandardDeviation(prices, middleBand);
        
        // Calculate Upper and Lower Bands
        BigDecimal bandWidth = standardDeviation.multiply(stdDevMultiplier);
        BigDecimal upperBand = middleBand.add(bandWidth);
        BigDecimal lowerBand = middleBand.subtract(bandWidth);
        
        // Determine signal based on current price position
        BigDecimal currentPrice = prices.get(prices.size() - 1);
        BollingerBandsSignal signal = determineSignal(currentPrice, upperBand, middleBand, lowerBand);
        String interpretation = generateInterpretation(currentPrice, upperBand, middleBand, 
                                                     lowerBand, signal, period);
        
        return new BollingerBandsResult(upperBand, middleBand, lowerBand, signal, interpretation);
    }
    
    /**
     * Calculate Simple Moving Average
     */
    private BigDecimal calculateSMA(List<BigDecimal> prices) {
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(prices.size()), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate Standard Deviation
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> prices, BigDecimal mean) {
        if (prices.size() <= 1) {
            return BigDecimal.ZERO;
        }
        
        // Calculate variance
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal price : prices) {
            BigDecimal difference = price.subtract(mean);
            variance = variance.add(difference.multiply(difference));
        }
        
        variance = variance.divide(new BigDecimal(prices.size() - 1), 6, RoundingMode.HALF_UP);
        
        // Calculate standard deviation (square root of variance)
        return sqrt(variance);
    }
    
    /**
     * Calculate square root using Newton's method
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value.divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        BigDecimal previous;
        
        // Newton's method iteration
        for (int i = 0; i < 10; i++) {
            previous = x;
            x = x.add(value.divide(x, 6, RoundingMode.HALF_UP))
                 .divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
            
            // Check for convergence
            if (x.subtract(previous).abs().compareTo(new BigDecimal("0.0001")) < 0) {
                break;
            }
        }
        
        return x;
    }
    
    /**
     * Determine trading signal based on price position relative to bands
     */
    private BollingerBandsSignal determineSignal(BigDecimal currentPrice, BigDecimal upperBand,
                                                BigDecimal middleBand, BigDecimal lowerBand) {
        // Calculate band width for squeeze detection
        BigDecimal bandWidth = upperBand.subtract(lowerBand);
        BigDecimal priceRange = upperBand.subtract(lowerBand);
        BigDecimal middleRange = middleBand.multiply(new BigDecimal("0.05")); // 5% of middle band
        
        // Check for band squeeze (low volatility)
        if (bandWidth.compareTo(middleRange) < 0) {
            return BollingerBandsSignal.SQUEEZE;
        }
        
        // Calculate price position as percentage of band width
        BigDecimal pricePosition = currentPrice.subtract(lowerBand)
                .divide(bandWidth, 4, RoundingMode.HALF_UP);
        
        // Determine signal based on position
        if (pricePosition.compareTo(new BigDecimal("0.95")) >= 0) {
            return BollingerBandsSignal.OVERBOUGHT;
        } else if (pricePosition.compareTo(new BigDecimal("0.80")) >= 0) {
            return BollingerBandsSignal.APPROACHING_UPPER;
        } else if (pricePosition.compareTo(new BigDecimal("0.20")) <= 0) {
            return BollingerBandsSignal.APPROACHING_LOWER;
        } else if (pricePosition.compareTo(new BigDecimal("0.05")) <= 0) {
            return BollingerBandsSignal.OVERSOLD;
        } else {
            return BollingerBandsSignal.NEUTRAL;
        }
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generateInterpretation(BigDecimal currentPrice, BigDecimal upperBand,
                                        BigDecimal middleBand, BigDecimal lowerBand,
                                        BollingerBandsSignal signal, int period) {
        String baseInfo = String.format("BB(%d): Upper=%.2f, Middle=%.2f, Lower=%.2f, Price=%.2f",
                                       period, upperBand, middleBand, lowerBand, currentPrice);
        
        return switch (signal) {
            case OVERBOUGHT -> baseInfo + " - Price near upper band, overbought condition";
            case APPROACHING_UPPER -> baseInfo + " - Price approaching upper band, potential resistance";
            case NEUTRAL -> baseInfo + " - Price in middle range, neutral condition";
            case APPROACHING_LOWER -> baseInfo + " - Price approaching lower band, potential support";
            case OVERSOLD -> baseInfo + " - Price near lower band, oversold condition";
            case SQUEEZE -> baseInfo + " - Band squeeze, low volatility, potential breakout";
        };
    }
    
    /**
     * Bollinger Bands calculation result
     */
    public static class BollingerBandsResult {
        private final BigDecimal upperBand;
        private final BigDecimal middleBand;
        private final BigDecimal lowerBand;
        private final BollingerBandsSignal signal;
        private final String interpretation;
        
        public BollingerBandsResult(BigDecimal upperBand, BigDecimal middleBand, BigDecimal lowerBand,
                                   BollingerBandsSignal signal, String interpretation) {
            this.upperBand = upperBand;
            this.middleBand = middleBand;
            this.lowerBand = lowerBand;
            this.signal = signal;
            this.interpretation = interpretation;
        }
        
        public BigDecimal getUpperBand() { return upperBand; }
        public BigDecimal getMiddleBand() { return middleBand; }
        public BigDecimal getLowerBand() { return lowerBand; }
        public BollingerBandsSignal getSignal() { return signal; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() {
            return signal == BollingerBandsSignal.OVERSOLD || 
                   signal == BollingerBandsSignal.APPROACHING_LOWER;
        }
        
        public boolean isBearish() {
            return signal == BollingerBandsSignal.OVERBOUGHT || 
                   signal == BollingerBandsSignal.APPROACHING_UPPER;
        }
        
        public boolean isNeutral() { 
            return signal == BollingerBandsSignal.NEUTRAL || 
                   signal == BollingerBandsSignal.SQUEEZE; 
        }
        
        public BigDecimal getBandWidth() {
            return upperBand.subtract(lowerBand);
        }
        
        @Override
        public String toString() {
            return String.format("BB(%.2f, %.2f, %.2f, %s)", 
                               upperBand, middleBand, lowerBand, signal);
        }
    }
    
    /**
     * Bollinger Bands signal types
     */
    public enum BollingerBandsSignal {
        OVERBOUGHT,         // Price near upper band (>95% of band width)
        APPROACHING_UPPER,  // Price approaching upper band (>80% of band width)
        NEUTRAL,           // Price in middle range
        APPROACHING_LOWER,  // Price approaching lower band (<20% of band width)
        OVERSOLD,          // Price near lower band (<5% of band width)
        SQUEEZE            // Band squeeze indicating low volatility
    }
}