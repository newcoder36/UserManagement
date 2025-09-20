package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) Indicator
 * 
 * MACD is a trend-following momentum indicator that shows the relationship 
 * between two moving averages of a security's price.
 * 
 * Components:
 * - MACD Line: 12-period EMA - 26-period EMA
 * - Signal Line: 9-period EMA of MACD Line
 * - Histogram: MACD Line - Signal Line
 * 
 * Trading signals:
 * - MACD above Signal: Bullish momentum
 * - MACD below Signal: Bearish momentum
 * - MACD crossover Signal: Trend change
 * - Histogram divergence: Momentum change
 */
@Component
public class MACDIndicator {
    
    private static final int FAST_PERIOD = 12;
    private static final int SLOW_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;
    
    @Autowired
    private MovingAverageIndicator movingAverageIndicator;
    
    /**
     * Calculate MACD with default periods (12, 26, 9)
     */
    public MACDResult calculateMACD(List<StockData> stockData) {
        return calculateMACD(stockData, FAST_PERIOD, SLOW_PERIOD, SIGNAL_PERIOD);
    }
    
    /**
     * Calculate MACD with custom periods
     */
    public MACDResult calculateMACD(List<StockData> stockData, int fastPeriod, 
                                   int slowPeriod, int signalPeriod) {
        if (stockData == null || stockData.size() < slowPeriod + signalPeriod) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    MACDSignal.NEUTRAL, "Insufficient data for MACD calculation");
        }
        
        // Sort by timestamp
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Calculate fast and slow EMAs
        List<BigDecimal> fastEMA = movingAverageIndicator.calculateEMAValues(sortedData, fastPeriod);
        List<BigDecimal> slowEMA = movingAverageIndicator.calculateEMAValues(sortedData, slowPeriod);
        
        if (fastEMA.isEmpty() || slowEMA.isEmpty()) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    MACDSignal.NEUTRAL, "Failed to calculate EMAs for MACD");
        }
        
        // Calculate MACD line values
        List<BigDecimal> macdValues = new ArrayList<>();
        int startIndex = slowPeriod - fastPeriod; // Align the EMAs
        
        for (int i = startIndex; i < fastEMA.size() && (i - startIndex) < slowEMA.size(); i++) {
            BigDecimal macdValue = fastEMA.get(i).subtract(slowEMA.get(i - startIndex));
            macdValues.add(macdValue);
        }
        
        if (macdValues.size() < signalPeriod) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    MACDSignal.NEUTRAL, "Insufficient MACD data for signal line calculation");
        }
        
        // Calculate signal line (EMA of MACD)
        List<BigDecimal> signalValues = calculateSignalLine(macdValues, signalPeriod);
        
        if (signalValues.isEmpty()) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    MACDSignal.NEUTRAL, "Failed to calculate signal line");
        }
        
        // Get current values (last in arrays)
        BigDecimal currentMACD = macdValues.get(macdValues.size() - 1);
        BigDecimal currentSignal = signalValues.get(signalValues.size() - 1);
        BigDecimal currentHistogram = currentMACD.subtract(currentSignal);
        
        // Determine signal
        MACDSignal signal = determineSignal(macdValues, signalValues);
        String interpretation = generateInterpretation(currentMACD, currentSignal, 
                                                     currentHistogram, signal);
        
        return new MACDResult(currentMACD, currentSignal, currentHistogram, signal, interpretation);
    }
    
    /**
     * Calculate signal line (EMA of MACD values)
     */
    private List<BigDecimal> calculateSignalLine(List<BigDecimal> macdValues, int signalPeriod) {
        List<BigDecimal> signalValues = new ArrayList<>();
        
        if (macdValues.size() < signalPeriod) {
            return signalValues;
        }
        
        // Calculate smoothing factor
        BigDecimal smoothing = new BigDecimal("2").divide(
                new BigDecimal(signalPeriod + 1), 6, RoundingMode.HALF_UP);
        
        // Initialize with SMA of first signalPeriod MACD values
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < signalPeriod; i++) {
            sum = sum.add(macdValues.get(i));
        }
        BigDecimal signal = sum.divide(new BigDecimal(signalPeriod), 6, RoundingMode.HALF_UP);
        signalValues.add(signal);
        
        // Calculate EMA for remaining values
        for (int i = signalPeriod; i < macdValues.size(); i++) {
            signal = macdValues.get(i).multiply(smoothing).add(
                    signal.multiply(BigDecimal.ONE.subtract(smoothing)));
            signalValues.add(signal);
        }
        
        return signalValues;
    }
    
    /**
     * Determine MACD trading signal
     */
    private MACDSignal determineSignal(List<BigDecimal> macdValues, List<BigDecimal> signalValues) {
        if (macdValues.size() < 2 || signalValues.size() < 2) {
            return MACDSignal.NEUTRAL;
        }
        
        int lastIndex = Math.min(macdValues.size(), signalValues.size()) - 1;
        int prevIndex = lastIndex - 1;
        
        BigDecimal currentMACD = macdValues.get(lastIndex);
        BigDecimal currentSignal = signalValues.get(lastIndex);
        BigDecimal previousMACD = macdValues.get(prevIndex);
        BigDecimal previousSignal = signalValues.get(prevIndex);
        
        boolean currentMACDAboveSignal = currentMACD.compareTo(currentSignal) > 0;
        boolean previousMACDAboveSignal = previousMACD.compareTo(previousSignal) > 0;
        
        // Check for crossovers
        if (!previousMACDAboveSignal && currentMACDAboveSignal) {
            return MACDSignal.BULLISH_CROSSOVER;
        } else if (previousMACDAboveSignal && !currentMACDAboveSignal) {
            return MACDSignal.BEARISH_CROSSOVER;
        }
        
        // Check current position
        if (currentMACDAboveSignal) {
            // Check if histogram is increasing (strengthening momentum)
            BigDecimal currentHistogram = currentMACD.subtract(currentSignal);
            BigDecimal previousHistogram = previousMACD.subtract(previousSignal);
            
            if (currentHistogram.compareTo(previousHistogram) > 0) {
                return MACDSignal.BULLISH_MOMENTUM;
            } else {
                return MACDSignal.BULLISH_WEAKENING;
            }
        } else {
            // Check if histogram is decreasing (strengthening momentum)
            BigDecimal currentHistogram = currentMACD.subtract(currentSignal);
            BigDecimal previousHistogram = previousMACD.subtract(previousSignal);
            
            if (currentHistogram.compareTo(previousHistogram) < 0) {
                return MACDSignal.BEARISH_MOMENTUM;
            } else {
                return MACDSignal.BEARISH_WEAKENING;
            }
        }
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generateInterpretation(BigDecimal macd, BigDecimal signal, 
                                        BigDecimal histogram, MACDSignal macdSignal) {
        String baseInfo = String.format("MACD: %.4f, Signal: %.4f, Histogram: %.4f", 
                                       macd, signal, histogram);
        
        return switch (macdSignal) {
            case BULLISH_CROSSOVER -> baseInfo + " - Bullish crossover, buy signal";
            case BEARISH_CROSSOVER -> baseInfo + " - Bearish crossover, sell signal";
            case BULLISH_MOMENTUM -> baseInfo + " - Strong bullish momentum";
            case BULLISH_WEAKENING -> baseInfo + " - Bullish but weakening momentum";
            case BEARISH_MOMENTUM -> baseInfo + " - Strong bearish momentum";
            case BEARISH_WEAKENING -> baseInfo + " - Bearish but weakening momentum";
            case NEUTRAL -> baseInfo + " - Neutral momentum";
        };
    }
    
    /**
     * MACD calculation result
     */
    public static class MACDResult {
        private final BigDecimal macdLine;
        private final BigDecimal signalLine;
        private final BigDecimal histogram;
        private final MACDSignal signal;
        private final String interpretation;
        
        public MACDResult(BigDecimal macdLine, BigDecimal signalLine, BigDecimal histogram,
                         MACDSignal signal, String interpretation) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
            this.signal = signal;
            this.interpretation = interpretation;
        }
        
        public BigDecimal getMacdLine() { return macdLine; }
        public BigDecimal getSignalLine() { return signalLine; }
        public BigDecimal getHistogram() { return histogram; }
        public MACDSignal getSignal() { return signal; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() {
            return signal == MACDSignal.BULLISH_CROSSOVER || 
                   signal == MACDSignal.BULLISH_MOMENTUM;
        }
        
        public boolean isBearish() {
            return signal == MACDSignal.BEARISH_CROSSOVER || 
                   signal == MACDSignal.BEARISH_MOMENTUM;
        }
        
        public boolean isNeutral() { return signal == MACDSignal.NEUTRAL; }
        
        @Override
        public String toString() {
            return String.format("MACD(%.4f, %.4f, %.4f, %s)", 
                               macdLine, signalLine, histogram, signal);
        }
    }
    
    /**
     * MACD signal types
     */
    public enum MACDSignal {
        BULLISH_CROSSOVER,    // MACD crosses above signal line
        BEARISH_CROSSOVER,    // MACD crosses below signal line
        BULLISH_MOMENTUM,     // MACD above signal with increasing histogram
        BULLISH_WEAKENING,    // MACD above signal with decreasing histogram
        BEARISH_MOMENTUM,     // MACD below signal with decreasing histogram
        BEARISH_WEAKENING,    // MACD below signal with increasing histogram
        NEUTRAL               // No clear signal
    }
}