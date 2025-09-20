package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume Analysis Indicator
 * 
 * Volume analysis helps confirm price movements and identify potential reversals:
 * - High volume with price increase: Strong bullish signal
 * - High volume with price decrease: Strong bearish signal
 * - Low volume with price movement: Weak signal
 * - Volume breakouts: Potential trend changes
 * 
 * Metrics calculated:
 * - Volume Moving Average (VMA)
 * - Volume Rate of Change
 * - Volume-Price Trend (VPT)
 * - Relative Volume
 */
@Component
public class VolumeIndicator {
    
    private static final int DEFAULT_PERIOD = 20;
    private static final BigDecimal HIGH_VOLUME_THRESHOLD = new BigDecimal("1.5"); // 150% of average
    private static final BigDecimal LOW_VOLUME_THRESHOLD = new BigDecimal("0.5");  // 50% of average
    
    /**
     * Calculate volume analysis with default period
     */
    public VolumeAnalysisResult analyzeVolume(List<StockData> stockData) {
        return analyzeVolume(stockData, DEFAULT_PERIOD);
    }
    
    /**
     * Calculate comprehensive volume analysis
     */
    public VolumeAnalysisResult analyzeVolume(List<StockData> stockData, int period) {
        if (stockData == null || stockData.size() < period) {
            return new VolumeAnalysisResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    VolumeSignal.NEUTRAL, "Insufficient data for volume analysis");
        }
        
        // Sort by timestamp
        List<StockData> sortedData = new ArrayList<>(stockData);
        sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Extract volumes and prices
        List<Long> volumes = new ArrayList<>();
        List<BigDecimal> prices = new ArrayList<>();
        
        for (StockData data : sortedData) {
            if (data.getVolume() != null && data.getLastPrice() != null) {
                volumes.add(data.getVolume());
                prices.add(data.getLastPrice());
            }
        }
        
        if (volumes.size() < period) {
            return new VolumeAnalysisResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    VolumeSignal.NEUTRAL, "Insufficient valid volume data");
        }
        
        // Calculate Volume Moving Average
        BigDecimal volumeMA = calculateVolumeMA(volumes, period);
        
        // Calculate current relative volume
        Long currentVolume = volumes.get(volumes.size() - 1);
        BigDecimal relativeVolume = new BigDecimal(currentVolume).divide(volumeMA, 4, RoundingMode.HALF_UP);
        
        // Calculate Volume-Price Trend (VPT)
        BigDecimal vpt = calculateVPT(prices, volumes);
        
        // Determine signal
        VolumeSignal signal = determineVolumeSignal(prices, volumes, relativeVolume, period);
        String interpretation = generateInterpretation(currentVolume, volumeMA, relativeVolume, 
                                                     vpt, signal);
        
        return new VolumeAnalysisResult(new BigDecimal(currentVolume), volumeMA, relativeVolume,
                signal, interpretation);
    }
    
    /**
     * Calculate Volume Moving Average
     */
    private BigDecimal calculateVolumeMA(List<Long> volumes, int period) {
        int startIndex = Math.max(0, volumes.size() - period);
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        
        for (int i = startIndex; i < volumes.size(); i++) {
            sum = sum.add(new BigDecimal(volumes.get(i)));
            count++;
        }
        
        return sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate Volume-Price Trend (VPT)
     * VPT = Previous VPT + Volume × (Current Price - Previous Price) / Previous Price
     */
    private BigDecimal calculateVPT(List<BigDecimal> prices, List<Long> volumes) {
        if (prices.size() < 2 || volumes.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal vpt = BigDecimal.ZERO;
        
        for (int i = 1; i < Math.min(prices.size(), volumes.size()); i++) {
            BigDecimal currentPrice = prices.get(i);
            BigDecimal previousPrice = prices.get(i - 1);
            Long currentVolume = volumes.get(i);
            
            if (previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal priceChange = currentPrice.subtract(previousPrice)
                        .divide(previousPrice, 6, RoundingMode.HALF_UP);
                BigDecimal volumeContribution = new BigDecimal(currentVolume).multiply(priceChange);
                vpt = vpt.add(volumeContribution);
            }
        }
        
        return vpt;
    }
    
    /**
     * Determine volume signal based on price-volume relationship
     */
    private VolumeSignal determineVolumeSignal(List<BigDecimal> prices, List<Long> volumes,
                                             BigDecimal relativeVolume, int period) {
        if (prices.size() < 2 || volumes.size() < 2) {
            return VolumeSignal.NEUTRAL;
        }
        
        // Get recent price change
        BigDecimal currentPrice = prices.get(prices.size() - 1);
        BigDecimal previousPrice = prices.get(prices.size() - 2);
        boolean priceUp = currentPrice.compareTo(previousPrice) > 0;
        boolean priceDown = currentPrice.compareTo(previousPrice) < 0;
        
        // Analyze volume level
        boolean highVolume = relativeVolume.compareTo(HIGH_VOLUME_THRESHOLD) >= 0;
        boolean lowVolume = relativeVolume.compareTo(LOW_VOLUME_THRESHOLD) <= 0;
        
        // Determine signal based on price-volume relationship
        if (highVolume && priceUp) {
            return VolumeSignal.STRONG_BULLISH;
        } else if (highVolume && priceDown) {
            return VolumeSignal.STRONG_BEARISH;
        } else if (lowVolume && priceUp) {
            return VolumeSignal.WEAK_BULLISH;
        } else if (lowVolume && priceDown) {
            return VolumeSignal.WEAK_BEARISH;
        } else if (highVolume) {
            // High volume but no significant price change might indicate accumulation/distribution
            return VolumeSignal.ACCUMULATION;
        } else {
            return VolumeSignal.NEUTRAL;
        }
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generateInterpretation(Long currentVolume, BigDecimal volumeMA,
                                        BigDecimal relativeVolume, BigDecimal vpt,
                                        VolumeSignal signal) {
        String baseInfo = String.format("Volume: %s, Avg: %.0f, Relative: %.2fx, VPT: %.0f",
                formatVolume(currentVolume), volumeMA, relativeVolume, vpt);
        
        return switch (signal) {
            case STRONG_BULLISH -> baseInfo + " - High volume with price increase, strong bullish signal";
            case WEAK_BULLISH -> baseInfo + " - Low volume with price increase, weak bullish signal";
            case STRONG_BEARISH -> baseInfo + " - High volume with price decrease, strong bearish signal";
            case WEAK_BEARISH -> baseInfo + " - Low volume with price decrease, weak bearish signal";
            case ACCUMULATION -> baseInfo + " - High volume, potential accumulation/distribution";
            case BREAKOUT -> baseInfo + " - Volume breakout, potential trend change";
            case NEUTRAL -> baseInfo + " - Normal volume activity";
        };
    }
    
    /**
     * Format volume with appropriate units
     */
    private String formatVolume(Long volume) {
        if (volume >= 10_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 100_000) {
            return String.format("%.0fK", volume / 1_000.0);
        } else {
            return volume.toString();
        }
    }
    
    /**
     * Volume analysis result
     */
    public static class VolumeAnalysisResult {
        private final BigDecimal currentVolume;
        private final BigDecimal averageVolume;
        private final BigDecimal relativeVolume;
        private final VolumeSignal signal;
        private final String interpretation;
        
        public VolumeAnalysisResult(BigDecimal currentVolume, BigDecimal averageVolume,
                                   BigDecimal relativeVolume, VolumeSignal signal,
                                   String interpretation) {
            this.currentVolume = currentVolume;
            this.averageVolume = averageVolume;
            this.relativeVolume = relativeVolume;
            this.signal = signal;
            this.interpretation = interpretation;
        }
        
        public BigDecimal getCurrentVolume() { return currentVolume; }
        public BigDecimal getAverageVolume() { return averageVolume; }
        public BigDecimal getRelativeVolume() { return relativeVolume; }
        public VolumeSignal getSignal() { return signal; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() {
            return signal == VolumeSignal.STRONG_BULLISH || signal == VolumeSignal.WEAK_BULLISH;
        }
        
        public boolean isBearish() {
            return signal == VolumeSignal.STRONG_BEARISH || signal == VolumeSignal.WEAK_BEARISH;
        }
        
        public boolean isNeutral() { return signal == VolumeSignal.NEUTRAL; }
        
        public boolean isHighVolume() {
            return relativeVolume.compareTo(HIGH_VOLUME_THRESHOLD) >= 0;
        }
        
        @Override
        public String toString() {
            return String.format("Volume(%.0f, %.2fx, %s)", 
                               currentVolume, relativeVolume, signal);
        }
    }
    
    /**
     * Volume signal types
     */
    public enum VolumeSignal {
        STRONG_BULLISH,    // High volume with price increase
        WEAK_BULLISH,      // Low volume with price increase
        STRONG_BEARISH,    // High volume with price decrease
        WEAK_BEARISH,      // Low volume with price decrease
        ACCUMULATION,      // High volume with minimal price change
        BREAKOUT,          // Volume breakout pattern
        NEUTRAL            // Normal volume activity
    }
}