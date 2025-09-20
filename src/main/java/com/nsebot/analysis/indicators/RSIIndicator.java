package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * RSI (Relative Strength Index) Technical Indicator
 * 
 * RSI is a momentum oscillator that measures the speed and magnitude of price changes.
 * Values range from 0-100:
 * - RSI > 70: Overbought (potential sell signal)
 * - RSI < 30: Oversold (potential buy signal)
 * - RSI 30-70: Neutral zone
 */
@Component
public class RSIIndicator {
    
    private static final int DEFAULT_PERIOD = 14;
    private static final BigDecimal OVERBOUGHT_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal OVERSOLD_THRESHOLD = new BigDecimal("30");
    
    /**
     * Calculate RSI for stock data using default 14-period
     */
    public RSIResult calculateRSI(List<StockData> stockData) {
        return calculateRSI(stockData, DEFAULT_PERIOD);
    }
    
    /**
     * Calculate RSI for stock data with custom period
     */
    public RSIResult calculateRSI(List<StockData> stockData, int period) {
        if (stockData == null || stockData.size() < period + 1) {
            return new RSIResult(BigDecimal.ZERO, RSISignal.NEUTRAL, "Insufficient data for RSI calculation");
        }
        
        // Sort by timestamp to ensure chronological order
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        
        // Calculate price changes and separate gains/losses
        for (int i = 1; i < sortedData.size(); i++) {
            BigDecimal currentPrice = sortedData.get(i).getLastPrice();
            BigDecimal previousPrice = sortedData.get(i - 1).getLastPrice();
            
            if (currentPrice != null && previousPrice != null) {
                BigDecimal change = currentPrice.subtract(previousPrice);
                
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    gains.add(change);
                    losses.add(BigDecimal.ZERO);
                } else {
                    gains.add(BigDecimal.ZERO);
                    losses.add(change.abs());
                }
            }
        }
        
        if (gains.size() < period) {
            return new RSIResult(BigDecimal.ZERO, RSISignal.NEUTRAL, "Insufficient price data for RSI calculation");
        }
        
        // Calculate initial average gain and loss
        BigDecimal initialAvgGain = calculateAverage(gains.subList(0, period));
        BigDecimal initialAvgLoss = calculateAverage(losses.subList(0, period));
        
        // Calculate current RSI using Wilder's smoothing method
        BigDecimal avgGain = initialAvgGain;
        BigDecimal avgLoss = initialAvgLoss;
        
        // Apply Wilder's smoothing for remaining periods
        for (int i = period; i < gains.size(); i++) {
            avgGain = avgGain.multiply(new BigDecimal(period - 1))
                    .add(gains.get(i))
                    .divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
            
            avgLoss = avgLoss.multiply(new BigDecimal(period - 1))
                    .add(losses.get(i))
                    .divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
        }
        
        // Calculate RSI
        BigDecimal rsi;
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            rsi = new BigDecimal("100");
        } else {
            BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
            rsi = new BigDecimal("100").subtract(
                    new BigDecimal("100").divide(
                            BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
        }
        
        // Determine signal based on RSI value
        RSISignal signal = determineSignal(rsi);
        String interpretation = generateInterpretation(rsi, signal);
        
        return new RSIResult(rsi, signal, interpretation);
    }
    
    /**
     * Calculate simple average of BigDecimal list
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(new BigDecimal(values.size()), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Determine trading signal based on RSI value
     */
    private RSISignal determineSignal(BigDecimal rsi) {
        if (rsi.compareTo(OVERBOUGHT_THRESHOLD) >= 0) {
            return RSISignal.OVERBOUGHT;
        } else if (rsi.compareTo(OVERSOLD_THRESHOLD) <= 0) {
            return RSISignal.OVERSOLD;
        } else {
            return RSISignal.NEUTRAL;
        }
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generateInterpretation(BigDecimal rsi, RSISignal signal) {
        String baseInterpretation = String.format("RSI: %.2f", rsi);
        
        return switch (signal) {
            case OVERBOUGHT -> baseInterpretation + " - Overbought condition, potential sell signal";
            case OVERSOLD -> baseInterpretation + " - Oversold condition, potential buy signal";
            case NEUTRAL -> baseInterpretation + " - Neutral zone, trend continuation likely";
        };
    }
    
    /**
     * RSI calculation result
     */
    public static class RSIResult {
        private final BigDecimal value;
        private final RSISignal signal;
        private final String interpretation;
        
        public RSIResult(BigDecimal value, RSISignal signal, String interpretation) {
            this.value = value;
            this.signal = signal;
            this.interpretation = interpretation;
        }
        
        public BigDecimal getValue() { return value; }
        public RSISignal getSignal() { return signal; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() { return signal == RSISignal.OVERSOLD; }
        public boolean isBearish() { return signal == RSISignal.OVERBOUGHT; }
        public boolean isNeutral() { return signal == RSISignal.NEUTRAL; }
        
        @Override
        public String toString() {
            return String.format("RSI(%.2f, %s)", value, signal);
        }
    }
    
    /**
     * RSI signal enumeration
     */
    public enum RSISignal {
        OVERBOUGHT,  // RSI > 70
        OVERSOLD,    // RSI < 30
        NEUTRAL      // RSI 30-70
    }
}