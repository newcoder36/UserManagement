package com.nsebot.service.impl;

import com.nsebot.client.NSEApiClient;
import com.nsebot.dto.StockData;
import com.nsebot.service.NSEDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Implementation of NSE Data Service using NSE API Client
 */
@Service
public class NSEDataServiceImpl implements NSEDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(NSEDataServiceImpl.class);
    
    private final NSEApiClient nseApiClient;
    
    @Autowired
    public NSEDataServiceImpl(NSEApiClient nseApiClient) {
        this.nseApiClient = nseApiClient;
    }
    
    @Override
    // @Cacheable(value = "stockData", key = "#symbol", unless = "#result == null || !#result.isPresent()")
    public Optional<StockData> getStockData(String symbol) {
        logger.debug("Fetching stock data for symbol: {}", symbol);
        
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Invalid symbol provided: {}", symbol);
            return Optional.empty();
        }
        
        return nseApiClient.getStockQuote(symbol.trim().toUpperCase());
    }
    
    @Override
    public List<StockData> getMultipleStockData(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.info("Fetching stock data for {} symbols", symbols.size());
        
        return symbols.parallelStream()
                .map(symbol -> getStockData(symbol))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    @Override
    // @Cacheable(value = "nifty100Symbols", unless = "#result == null || #result.isEmpty()")
    public List<String> getNifty100Symbols() {
        logger.debug("Fetching Nifty 100 symbols");
        return nseApiClient.getNifty100Symbols();
    }
    
    @Override
    // @Cacheable(value = "historicalData", key = "#symbol + '_' + #days", unless = "#result == null || #result.isEmpty()")
    public List<StockData> getHistoricalData(String symbol, int days) {
        logger.debug("Generating historical data for symbol: {} days: {}", symbol, days);
        
        if (symbol == null || symbol.trim().isEmpty() || days <= 0) {
            logger.warn("Invalid parameters for historical data: symbol={}, days={}", symbol, days);
            return new ArrayList<>();
        }
        
        try {
            // Get current stock data as base point
            Optional<StockData> currentDataOpt = getStockData(symbol);
            if (currentDataOpt.isEmpty()) {
                logger.warn("Cannot generate historical data without current data for symbol: {}", symbol);
                return generateFallbackHistoricalData(symbol, days);
            }
            
            return generateRealisticHistoricalData(currentDataOpt.get(), days);
            
        } catch (Exception e) {
            logger.error("Error generating historical data for symbol: {}", symbol, e);
            return generateFallbackHistoricalData(symbol, days);
        }
    }
    
    /**
     * Generate realistic historical data based on current stock data
     */
    private List<StockData> generateRealisticHistoricalData(StockData currentData, int days) {
        List<StockData> historicalData = new ArrayList<>();
        Random random = new Random();
        
        // Calculate base volatility for the stock (2-8% daily volatility)
        BigDecimal baseVolatility = calculateStockVolatility(currentData.getSymbol());
        
        // Start from current data and work backwards
        BigDecimal currentPrice = currentData.getLastPrice();
        LocalDateTime currentTime = currentData.getTimestamp();
        Long baseVolume = currentData.getVolume() != null ? currentData.getVolume() : 1000000L;
        
        for (int i = 0; i < days; i++) {
            LocalDateTime historicalTime = currentTime.minusDays(i + 1);
            
            // Generate price movement with trending and mean reversion
            BigDecimal priceChange = generatePriceMovement(currentPrice, baseVolatility, i, days, random);
            BigDecimal historicalPrice = currentPrice.subtract(priceChange);
            
            // Ensure price stays within reasonable bounds (±50% from current)
            BigDecimal minPrice = currentPrice.multiply(new BigDecimal("0.5"));
            BigDecimal maxPrice = currentPrice.multiply(new BigDecimal("1.5"));
            if (historicalPrice.compareTo(minPrice) < 0) historicalPrice = minPrice;
            if (historicalPrice.compareTo(maxPrice) > 0) historicalPrice = maxPrice;
            
            // Generate OHLC data
            OHLCData ohlc = generateOHLCData(historicalPrice, baseVolatility, random);
            
            // Generate volume with realistic patterns
            Long volume = generateVolume(baseVolume, i, random);
            
            // Create historical stock data point
            StockData historicalPoint = createHistoricalDataPoint(
                currentData.getSymbol(), 
                currentData.getCompanyName(),
                historicalTime,
                ohlc,
                volume
            );
            
            historicalData.add(0, historicalPoint); // Add at beginning for chronological order
            currentPrice = historicalPrice; // Use this as base for next iteration
        }
        
        logger.debug("Generated {} days of historical data for {}", days, currentData.getSymbol());
        return historicalData;
    }
    
    /**
     * Calculate stock-specific volatility based on symbol characteristics
     */
    private BigDecimal calculateStockVolatility(String symbol) {
        // Base volatility ranges by stock type
        BigDecimal baseVolatility = new BigDecimal("0.025"); // 2.5% default
        
        // Adjust volatility based on stock characteristics
        if (isLargeCap(symbol)) {
            baseVolatility = new BigDecimal("0.020"); // 2% for large caps
        } else if (isMidCap(symbol)) {
            baseVolatility = new BigDecimal("0.030"); // 3% for mid caps
        } else {
            baseVolatility = new BigDecimal("0.045"); // 4.5% for small caps
        }
        
        // Add sector-specific volatility
        baseVolatility = baseVolatility.add(getSectorVolatility(symbol));
        
        return baseVolatility;
    }
    
    /**
     * Generate price movement with trending and mean reversion patterns
     */
    private BigDecimal generatePriceMovement(BigDecimal currentPrice, BigDecimal volatility, 
                                           int dayIndex, int totalDays, Random random) {
        
        // Create trending behavior (stocks tend to trend)
        double trendStrength = 0.3; // 30% trend, 70% random walk
        double trend = (random.nextGaussian() * 0.1) + (dayIndex * 0.002); // Slight upward bias over time
        
        // Mean reversion component (prices tend to revert to mean)
        double meanReversion = Math.sin((dayIndex * Math.PI) / (totalDays * 0.5)) * 0.1;
        
        // Random component
        double randomWalk = random.nextGaussian() * volatility.doubleValue();
        
        // Combine components
        double totalMovement = (trend * trendStrength) + meanReversion + randomWalk;
        
        return currentPrice.multiply(BigDecimal.valueOf(Math.abs(totalMovement)))
                          .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Generate OHLC (Open, High, Low, Close) data for a trading day
     */
    private OHLCData generateOHLCData(BigDecimal closePrice, BigDecimal volatility, Random random) {
        // Generate intraday volatility (typically 0.5-2% of price)
        BigDecimal intradayRange = closePrice.multiply(volatility).multiply(new BigDecimal("0.8"));
        
        // Generate realistic OHLC relationships
        BigDecimal rangeFactor = BigDecimal.valueOf(random.nextDouble() * 0.6 + 0.2); // 0.2 to 0.8
        BigDecimal actualRange = intradayRange.multiply(rangeFactor);
        
        // Generate high and low around close price
        BigDecimal high = closePrice.add(actualRange.multiply(BigDecimal.valueOf(random.nextDouble())));
        BigDecimal low = closePrice.subtract(actualRange.multiply(BigDecimal.valueOf(random.nextDouble())));
        
        // Generate open price (typically close to previous close, which is our close)
        BigDecimal openGap = actualRange.multiply(BigDecimal.valueOf(random.nextGaussian() * 0.2));
        BigDecimal open = closePrice.add(openGap);
        
        // Ensure OHLC relationships are valid: High >= max(O,C), Low <= min(O,C)
        BigDecimal maxOC = open.max(closePrice);
        BigDecimal minOC = open.min(closePrice);
        
        if (high.compareTo(maxOC) < 0) high = maxOC.add(actualRange.multiply(new BigDecimal("0.1")));
        if (low.compareTo(minOC) > 0) low = minOC.subtract(actualRange.multiply(new BigDecimal("0.1")));
        
        return new OHLCData(
            open.setScale(2, RoundingMode.HALF_UP),
            high.setScale(2, RoundingMode.HALF_UP),
            low.setScale(2, RoundingMode.HALF_UP),
            closePrice.setScale(2, RoundingMode.HALF_UP)
        );
    }
    
    /**
     * Generate realistic trading volume
     */
    private Long generateVolume(Long baseVolume, int dayIndex, Random random) {
        // Volume patterns: higher on Mondays/Fridays, lower mid-week, seasonal variations
        double volumeMultiplier = 0.7 + (random.nextDouble() * 0.6); // 0.7 to 1.3 multiplier
        
        // Add day-of-week effect simulation
        int dayOfWeek = dayIndex % 7;
        if (dayOfWeek == 0 || dayOfWeek == 6) { // Weekend effect (though NSE closed)
            volumeMultiplier *= 0.3;
        } else if (dayOfWeek == 1 || dayOfWeek == 5) { // Monday/Friday higher volume
            volumeMultiplier *= 1.2;
        }
        
        // Add occasional volume spikes (news/events)
        if (random.nextDouble() < 0.05) { // 5% chance of volume spike
            volumeMultiplier *= (2 + random.nextDouble() * 3); // 2x to 5x spike
        }
        
        Long volume = Math.round(baseVolume * volumeMultiplier);
        return Math.max(volume, 1000L); // Minimum 1000 volume
    }
    
    /**
     * Create a historical stock data point
     */
    private StockData createHistoricalDataPoint(String symbol, String companyName, 
                                              LocalDateTime timestamp, OHLCData ohlc, Long volume) {
        StockData historicalData = new StockData(symbol, companyName);
        historicalData.setTimestamp(timestamp);
        historicalData.setLastPrice(ohlc.close);
        historicalData.setOpenPrice(ohlc.open);
        historicalData.setDayHigh(ohlc.high);
        historicalData.setDayLow(ohlc.low);
        historicalData.setVolume(volume);
        
        // Calculate change and percentage change (mock previous close)
        BigDecimal previousClose = ohlc.close.multiply(new BigDecimal("0.998")); // Assume small gain
        historicalData.setPreviousClose(previousClose);
        historicalData.setChange(ohlc.close.subtract(previousClose));
        
        if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentChange = historicalData.getChange()
                    .divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            historicalData.setPercentChange(percentChange);
        }
        
        // Calculate turnover (Price * Volume)
        historicalData.setTurnover(ohlc.close.multiply(new BigDecimal(volume)));
        
        return historicalData;
    }
    
    /**
     * Generate fallback historical data when current data is not available
     */
    private List<StockData> generateFallbackHistoricalData(String symbol, int days) {
        logger.info("Generating fallback historical data for symbol: {}", symbol);
        
        // Use symbol-based default pricing
        BigDecimal basePrice = getDefaultPriceForSymbol(symbol);
        Long baseVolume = getDefaultVolumeForSymbol(symbol);
        
        StockData mockCurrentData = new StockData(symbol, getCompanyName(symbol));
        mockCurrentData.setLastPrice(basePrice);
        mockCurrentData.setVolume(baseVolume);
        mockCurrentData.setTimestamp(LocalDateTime.now());
        
        return generateRealisticHistoricalData(mockCurrentData, days);
    }
    
    /**
     * Helper methods for stock classification and defaults
     */
    private boolean isLargeCap(String symbol) {
        List<String> largeCapStocks = List.of(
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "HINDUNILVR", "ICICIBANK", 
            "HDFC", "ITC", "KOTAKBANK", "LT", "AXISBANK", "BHARTIARTL"
        );
        return largeCapStocks.contains(symbol);
    }
    
    private boolean isMidCap(String symbol) {
        List<String> midCapStocks = List.of(
            "ASIANPAINT", "MARUTI", "BAJFINANCE", "NESTLEIND", "HCLTECH", "WIPRO",
            "ULTRACEMCO", "SUNPHARMA", "TITAN", "POWERGRID", "M&M", "TECHM"
        );
        return midCapStocks.contains(symbol);
    }
    
    private BigDecimal getSectorVolatility(String symbol) {
        // IT stocks: lower volatility
        if (List.of("TCS", "INFY", "HCLTECH", "WIPRO", "TECHM", "MPHASIS", "LTIM").contains(symbol)) {
            return new BigDecimal("0.005");
        }
        // Banking: moderate volatility
        if (List.of("HDFCBANK", "ICICIBANK", "AXISBANK", "KOTAKBANK", "SBIN", "INDUSINDBK").contains(symbol)) {
            return new BigDecimal("0.010");
        }
        // Auto: higher volatility
        if (List.of("MARUTI", "M&M", "EICHERMOT", "BAJAJ-AUTO", "HEROMOTOCO", "TATAMOTORS").contains(symbol)) {
            return new BigDecimal("0.015");
        }
        // Default: moderate
        return new BigDecimal("0.008");
    }
    
    private BigDecimal getDefaultPriceForSymbol(String symbol) {
        // Realistic price ranges for major stocks
        return switch (symbol) {
            case "RELIANCE" -> new BigDecimal("2500");
            case "TCS" -> new BigDecimal("3800");
            case "HDFCBANK" -> new BigDecimal("1550");
            case "INFY" -> new BigDecimal("1720");
            case "ICICIBANK" -> new BigDecimal("1180");
            case "HDFC" -> new BigDecimal("2650");
            case "ITC" -> new BigDecimal("450");
            case "KOTAKBANK" -> new BigDecimal("1800");
            case "LT" -> new BigDecimal("3200");
            case "AXISBANK" -> new BigDecimal("1200");
            default -> new BigDecimal("1000"); // Default price
        };
    }
    
    private Long getDefaultVolumeForSymbol(String symbol) {
        // Volume ranges based on stock liquidity
        if (isLargeCap(symbol)) {
            return 2_000_000L; // 2M shares
        } else if (isMidCap(symbol)) {
            return 800_000L; // 800K shares
        } else {
            return 200_000L; // 200K shares
        }
    }
    
    private String getCompanyName(String symbol) {
        return switch (symbol) {
            case "RELIANCE" -> "Reliance Industries Limited";
            case "TCS" -> "Tata Consultancy Services Limited";
            case "HDFCBANK" -> "HDFC Bank Limited";
            case "INFY" -> "Infosys Limited";
            case "ICICIBANK" -> "ICICI Bank Limited";
            case "HDFC" -> "Housing Development Finance Corporation Limited";
            case "ITC" -> "ITC Limited";
            case "KOTAKBANK" -> "Kotak Mahindra Bank Limited";
            case "LT" -> "Larsen & Toubro Limited";
            case "AXISBANK" -> "Axis Bank Limited";
            default -> symbol + " Limited";
        };
    }
    
    /**
     * Helper class for OHLC data
     */
    private static class OHLCData {
        final BigDecimal open;
        final BigDecimal high;
        final BigDecimal low;
        final BigDecimal close;
        
        OHLCData(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }
    
    /**
     * Check if NSE market is currently open
     * @return true if market is open
     */
    // @Cacheable(value = "marketStatus", unless = "!#result")
    public boolean isMarketOpen() {
        return nseApiClient.isMarketOpen();
    }
}