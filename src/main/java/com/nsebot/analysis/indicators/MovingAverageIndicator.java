package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Moving Average Indicators - SMA (Simple) and EMA (Exponential)
 * 
 * Moving averages smooth price data to identify trends:
 * - SMA: Simple average of prices over N periods
 * - EMA: Exponential average giving more weight to recent prices
 * 
 * Trading signals:
 * - Price above MA: Bullish trend
 * - Price below MA: Bearish trend
 * - MA crossovers: Trend change signals
 */
@Component
public class MovingAverageIndicator {
    
    /**
     * Calculate Simple Moving Average (SMA)
     */
    public MovingAverageResult calculateSMA(List<StockData> stockData, int period) {
        if (stockData == null || stockData.size() < period) {
            return new MovingAverageResult(BigDecimal.ZERO, MovingAverageSignal.NEUTRAL, 
                    "Insufficient data for SMA calculation", MovingAverageType.SMA);
        }
        
        // Sort by timestamp
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Get last 'period' prices
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = Math.max(0, sortedData.size() - period); i < sortedData.size(); i++) {
            BigDecimal price = sortedData.get(i).getLastPrice();
            if (price != null) {
                prices.add(price);
            }
        }
        
        if (prices.size() < period) {
            return new MovingAverageResult(BigDecimal.ZERO, MovingAverageSignal.NEUTRAL, 
                    "Insufficient valid price data for SMA calculation", MovingAverageType.SMA);
        }
        
        // Calculate SMA
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sma = sum.divide(new BigDecimal(prices.size()), 4, RoundingMode.HALF_UP);
        
        // Determine signal
        BigDecimal currentPrice = sortedData.get(sortedData.size() - 1).getLastPrice();
        MovingAverageSignal signal = determineSignal(currentPrice, sma);
        String interpretation = generateInterpretation(sma, signal, period, MovingAverageType.SMA);
        
        return new MovingAverageResult(sma, signal, interpretation, MovingAverageType.SMA);
    }
    
    /**
     * Calculate Exponential Moving Average (EMA)
     */
    public MovingAverageResult calculateEMA(List<StockData> stockData, int period) {
        if (stockData == null || stockData.size() < period) {
            return new MovingAverageResult(BigDecimal.ZERO, MovingAverageSignal.NEUTRAL, 
                    "Insufficient data for EMA calculation", MovingAverageType.EMA);
        }
        
        // Sort by timestamp
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Extract prices
        List<BigDecimal> prices = new ArrayList<>();
        for (StockData data : sortedData) {
            if (data.getLastPrice() != null) {
                prices.add(data.getLastPrice());
            }
        }
        
        if (prices.size() < period) {
            return new MovingAverageResult(BigDecimal.ZERO, MovingAverageSignal.NEUTRAL, 
                    "Insufficient valid price data for EMA calculation", MovingAverageType.EMA);
        }
        
        // Calculate smoothing factor
        BigDecimal smoothing = new BigDecimal("2").divide(
                new BigDecimal(period + 1), 6, RoundingMode.HALF_UP);
        
        // Initialize EMA with first SMA
        BigDecimal ema = calculateInitialSMA(prices.subList(0, period));
        
        // Calculate EMA for remaining periods
        for (int i = period; i < prices.size(); i++) {
            BigDecimal currentPrice = prices.get(i);
            ema = currentPrice.multiply(smoothing).add(
                    ema.multiply(BigDecimal.ONE.subtract(smoothing)));
        }
        
        // Determine signal
        BigDecimal currentPrice = prices.get(prices.size() - 1);
        MovingAverageSignal signal = determineSignal(currentPrice, ema);
        String interpretation = generateInterpretation(ema, signal, period, MovingAverageType.EMA);
        
        return new MovingAverageResult(ema, signal, interpretation, MovingAverageType.EMA);
    }
    
    /**
     * Calculate multiple EMAs for MACD calculation
     */
    public List<BigDecimal> calculateEMAValues(List<StockData> stockData, int period) {
        List<BigDecimal> emaValues = new ArrayList<>();
        
        if (stockData == null || stockData.size() < period) {
            return emaValues;
        }
        
        // Sort and extract prices
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        List<BigDecimal> prices = new ArrayList<>();
        for (StockData data : sortedData) {
            if (data.getLastPrice() != null) {
                prices.add(data.getLastPrice());
            }
        }
        
        if (prices.size() < period) {
            return emaValues;
        }
        
        // Calculate smoothing factor
        BigDecimal smoothing = new BigDecimal("2").divide(
                new BigDecimal(period + 1), 6, RoundingMode.HALF_UP);
        
        // Initialize EMA with first SMA
        BigDecimal ema = calculateInitialSMA(prices.subList(0, period));
        emaValues.add(ema);
        
        // Calculate EMA for remaining periods
        for (int i = period; i < prices.size(); i++) {
            BigDecimal currentPrice = prices.get(i);
            ema = currentPrice.multiply(smoothing).add(
                    ema.multiply(BigDecimal.ONE.subtract(smoothing)));
            emaValues.add(ema);
        }
        
        return emaValues;
    }
    
    /**
     * Calculate initial SMA for EMA seed
     */
    private BigDecimal calculateInitialSMA(List<BigDecimal> prices) {
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(prices.size()), 6, RoundingMode.HALF_UP);
    }
    
    /**
     * Determine trading signal
     */
    private MovingAverageSignal determineSignal(BigDecimal currentPrice, BigDecimal movingAverage) {
        if (currentPrice == null || movingAverage == null) {
            return MovingAverageSignal.NEUTRAL;
        }
        
        BigDecimal percentDiff = currentPrice.subtract(movingAverage)
                .divide(movingAverage, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        if (percentDiff.compareTo(new BigDecimal("2")) > 0) {
            return MovingAverageSignal.STRONG_BULLISH;
        } else if (percentDiff.compareTo(BigDecimal.ZERO) > 0) {
            return MovingAverageSignal.BULLISH;
        } else if (percentDiff.compareTo(new BigDecimal("-2")) < 0) {
            return MovingAverageSignal.STRONG_BEARISH;
        } else if (percentDiff.compareTo(BigDecimal.ZERO) < 0) {
            return MovingAverageSignal.BEARISH;
        } else {
            return MovingAverageSignal.NEUTRAL;
        }
    }
    
    /**
     * Generate interpretation
     */
    private String generateInterpretation(BigDecimal ma, MovingAverageSignal signal, 
                                        int period, MovingAverageType type) {
        String baseInterpretation = String.format("%s(%d): %.2f", type, period, ma);
        
        return switch (signal) {
            case STRONG_BULLISH -> baseInterpretation + " - Strong bullish trend, price well above MA";
            case BULLISH -> baseInterpretation + " - Bullish trend, price above MA";
            case NEUTRAL -> baseInterpretation + " - Neutral, price near MA";
            case BEARISH -> baseInterpretation + " - Bearish trend, price below MA";
            case STRONG_BEARISH -> baseInterpretation + " - Strong bearish trend, price well below MA";
        };
    }
    
    /**
     * Moving Average result class
     */
    public static class MovingAverageResult {
        private final BigDecimal value;
        private final MovingAverageSignal signal;
        private final String interpretation;
        private final MovingAverageType type;
        
        public MovingAverageResult(BigDecimal value, MovingAverageSignal signal, 
                                 String interpretation, MovingAverageType type) {
            this.value = value;
            this.signal = signal;
            this.interpretation = interpretation;
            this.type = type;
        }
        
        public BigDecimal getValue() { return value; }
        public MovingAverageSignal getSignal() { return signal; }
        public String getInterpretation() { return interpretation; }
        public MovingAverageType getType() { return type; }
        
        public boolean isBullish() { 
            return signal == MovingAverageSignal.BULLISH || signal == MovingAverageSignal.STRONG_BULLISH; 
        }
        public boolean isBearish() { 
            return signal == MovingAverageSignal.BEARISH || signal == MovingAverageSignal.STRONG_BEARISH; 
        }
        public boolean isNeutral() { return signal == MovingAverageSignal.NEUTRAL; }
        
        @Override
        public String toString() {
            return String.format("%s(%.2f, %s)", type, value, signal);
        }
    }
    
    /**
     * Moving Average signal types
     */
    public enum MovingAverageSignal {
        STRONG_BULLISH,  // Price > 2% above MA
        BULLISH,         // Price above MA
        NEUTRAL,         // Price near MA
        BEARISH,         // Price below MA
        STRONG_BEARISH   // Price > 2% below MA
    }
    
    /**
     * Moving Average types
     */
    public enum MovingAverageType {
        SMA,  // Simple Moving Average
        EMA   // Exponential Moving Average
    }
}