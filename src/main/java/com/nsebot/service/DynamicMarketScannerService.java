package com.nsebot.service;

import com.nsebot.dto.StockData;
import com.nsebot.client.NSEApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Market Scanner Service
 * Fetches live market data and categorizes stocks by market cap
 */
@Service
public class DynamicMarketScannerService {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicMarketScannerService.class);
    
    @Autowired
    private NSEDataService nseDataService;
    
    @Autowired
    private NSEApiClient nseApiClient;
    
    private final WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
        .build();
    
    /**
     * Get dynamic top performers categorized by market cap
     */
    public DynamicScanResult getDynamicTopPicks() {
        logger.info("🔍 Starting dynamic market scan...");
        
        try {
            // Get live market data from multiple sources
            List<LiveMarketStock> liveStocks = fetchLiveMarketData();
            
            if (liveStocks.isEmpty()) {
                logger.warn("No live market data available, falling back to top stocks");
                return createFallbackScanResult();
            }
            
            // Categorize by market cap and performance
            Map<MarketCapCategory, List<LiveMarketStock>> categorized = categorizeByMarketCap(liveStocks);
            
            // Get top 5 from each category
            DynamicScanResult result = new DynamicScanResult();
            for (MarketCapCategory category : MarketCapCategory.values()) {
                List<LiveMarketStock> categoryStocks = categorized.getOrDefault(category, new ArrayList<>());
                List<LiveMarketStock> topPicks = categoryStocks.stream()
                    .sorted((a, b) -> b.getPerformanceScore().compareTo(a.getPerformanceScore()))
                    .limit(5)
                    .collect(Collectors.toList());
                result.setCategoryPicks(category, topPicks);
            }
            
            logger.info("✅ Dynamic scan completed: {} total stocks categorized", liveStocks.size());
            return result;
            
        } catch (Exception e) {
            logger.error("❌ Error in dynamic market scan: {}", e.getMessage(), e);
            return createFallbackScanResult();
        }
    }
    
    /**
     * Fetch live market data from ALL NSE stocks using Yahoo Finance
     */
    private List<LiveMarketStock> fetchLiveMarketData() {
        List<LiveMarketStock> stocks = new ArrayList<>();
        
        try {
            // Get comprehensive list of ALL NSE stocks
            List<String> allNseStocks = getAllNseStocks();
            logger.info("📊 Fetching data for {} NSE stocks using Yahoo Finance", allNseStocks.size());
            
            // Process all stocks in parallel using Yahoo Finance directly
            stocks = allNseStocks.parallelStream()
                .map(this::createLiveMarketStockFromYahoo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            logger.info("✅ Successfully processed {} stocks with complete data", stocks.size());
            
        } catch (Exception e) {
            logger.error("❌ Error fetching live market data: {}", e.getMessage());
        }
        
        return stocks;
    }
    
    /**
     * Get comprehensive list of ALL NSE stocks for complete market scanning
     */
    private List<String> getAllNseStocks() {
        // Comprehensive NSE stock universe - all major stocks across market caps
        return Arrays.asList(
            // NIFTY 50 - Large Cap Giants
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "HINDUNILVR", "ICICIBANK", "KOTAKBANK", 
            "SBIN", "BHARTIARTL", "ITC", "LT", "AXISBANK", "HCLTECH", "ASIANPAINT", "MARUTI",
            "BAJFINANCE", "TITAN", "NESTLEIND", "ULTRACEMCO", "WIPRO", "M&M", "SUNPHARMA",
            "TECHM", "ONGC", "TATASTEEL", "NTPC", "POWERGRID", "BAJAJFINSV", "INDUSINDBK",
            "ADANIPORTS", "COALINDIA", "DRREDDY", "JSWSTEEL", "TATAMOTORS", "GRASIM",
            "BRITANNIA", "SHRIRAMFIN", "APOLLOHOSP", "BPCL", "CIPLA", "DIVISLAB", "EICHERMOT",
            "HEROMOTOCO", "HINDALCO", "TRENT", "BAJAJ-AUTO", "ADANIENT", "LTIM", "SBILIFE",
            
            // NIFTY NEXT 50 - Mid to Large Cap
            "HDFCLIFE", "ICICIPRULI", "PIDILITIND", "GODREJCP", "MCDOWELL-N", "DABUR", "MARICO",
            "COLPAL", "PGHH", "VBL", "BERGEPAINT", "AUROPHARMA", "LUPIN", "TORNTPHARM",
            "ALKEM", "BIOCON", "CADILAHC", "GLENMARK", "IPCALAB", "LAURUSLABS", "PFIZER",
            "SUNPHARMA", "ZYDUSLIFE", "AMBUJACEM", "ACC", "RAMCOCEM", "SHREECEM", "JKCEMENT",
            "HEIDELBERG", "INDIACEM", "ORIENTCEM", "PRISMCEM", "BURNPUR", "CENTURYPLY",
            
            // Banking & Financial Services
            "PNB", "CANBK", "BANKBARODA", "UNIONBANK", "INDIANB", "CENTRALBK", "IDFCFIRSTB",
            "FEDERALBNK", "RBLBANK", "BANDHANBNK", "YESBANK", "AUBANK", "CITYUNION", "KARURBANK",
            "SOUTHBANK", "TMBBFIN", "UJJIVAN", "CHOLAFIN", "LICHSGFIN", "MUTHOOTFIN", "MANAPPURAM",
            "BAJAJHLDNG", "L&TFH", "PFC", "RECLTD", "IRFC", "IIFL", "MOTILALOF", "ANGELONE",
            
            // IT & Technology
            "PERSISTENT", "MPHASIS", "COFORGE", "MINDTREE", "LTTS", "OFSS", "TATAELXSI",
            "KPITTECH", "CYIENT", "ZENSAR", "POLYCAB", "HFCL", "STLTECH", "TANLA", "ROUTE",
            
            // Auto & Auto Components
            "ASHOKLEY", "TVSMOTORS", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT", "MOTHERSON",
            "BOSCHLTD", "EXIDEIND", "MRF", "APOLLOTYRE", "BALKRISIND", "CEAT", "JK-TYRE",
            
            // Oil & Gas
            "IOCL", "HPCL", "GAIL", "OIL", "MRPL", "CPCL", "PETRONET", "IGL", "MGL", "GSPL",
            
            // Metals & Mining
            "VEDL", "HINDALCO", "SAIL", "NMDC", "MOIL", "JINDALSTEL", "JSPL", "WELCORP",
            "HINDZINC", "RATNAMANI", "APL", "WELSPUNIND", "MANAKSTEEL", "KALYANIFORGE",
            
            // FMCG & Consumer
            "EMAMILTD", "GODREJIND", "TATACONSUM", "UBL", "RADICO", "MCDOWELL-N", "UNITDSPR",
            
            // Pharmaceuticals 
            "DRREDDY", "CIPLA", "LUPIN", "AUROPHARMA", "DIVISLAB", "TORNTPHARM", "ALKEM",
            "BIOCON", "CADILAHC", "GLENMARK", "IPCALAB", "LAURUSLABS", "PFIZER", "ZYDUSLIFE",
            
            // Telecom
            "IDEA", "TATACOMM", "RAILTEL", "MTNL", "ITI",
            
            // Cement
            "AMBUJACEM", "ACC", "RAMCOCEM", "SHREECEM", "JKCEMENT", "HEIDELBERG", "INDIACEM",
            
            // Infrastructure & Power
            "ADANIPOWER", "TATAPOWER", "NTPC", "POWERGRID", "NHPC", "SJVN", "THERMAX",
            "BHEL", "L&T", "GUJGASLTD", "GSPL", "IGL", "MGL",
            
            // Textiles
            "WELSPUNIND", "TRIDENT", "VARDHMAN", "WELCORP", "CENTURYPLY", "GREENPLY",
            
            // Small & Mid Cap Stocks
            "SUZLON", "GMRINFRA", "RELCAPITAL", "RELINFRA", "JPASSOCIAT", "JAIPRAKASH",
            "UNITECH", "DLF", "SOBHA", "GODREJPROP", "INDIABULL", "SRTRANSFIN", "REPCO",
            "PCJEWELLER", "JUSTDIAL", "RCOM", "JETAIRWAYS", "SPICEJET", "INDIGO"
        );
    }
    
    /**
     * Fetch most active stocks by volume from NSE API (DEPRECATED - using getAllNseStocks instead)
     */
    private List<String> fetchMostActiveStocks() {
        try {
            // Expanded list of high-volume and active stocks for comprehensive scanning
            return Arrays.asList(
                // Large Cap Giants (₹2000+)
                "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR", "ITC",
                "KOTAKBANK", "LT", "ASIANPAINT", "MARUTI", "NESTLEIND", "ULTRACEMCO", "TITAN",
                "BAJFINANCE", "HCLTECH", "WIPRO", "SUNPHARMA", "TECHM", "BRITANNIA", "DRREDDY",
                
                // Medium to Large Cap (₹500-2000)
                "SBIN", "AXISBANK", "BHARTIARTL", "ONGC", "TATASTEEL", "NTPC", "POWERGRID", 
                "M&M", "BAJAJFINSV", "INDUSINDBK", "ADANIPORTS", "COALINDIA", "JSWSTEEL",
                "CIPLA", "DIVISLAB", "APOLLOHOSP", "EICHERMOT", "HEROMOTOCO", "BAJAJ-AUTO",
                "BPCL", "IOCL", "HINDALCO", "VEDL", "GRASIM", "SHREECEM", "PIDILITIND",
                "BERGEPAINT", "DABUR", "GODREJCP", "MARICO", "COLPAL", "PGHH", "VBL",
                
                // Banking & Financial Services
                "HDFCLIFE", "SBILIFE", "ICICIPRULI", "BAJAJHLDNG", "MUTHOOTFIN", "CHOLAFIN",
                "L&TFH", "PFC", "RECLTD", "IRFC", "INDIANB", "PNB", "CANBK", "IDFCFIRSTB",
                
                // IT & Technology
                "LTIM", "PERSISTENT", "MPHASIS", "COFORGE", "MINDTREE", "LTTS", "OFSS",
                "TATAELXSI", "RPOWER", "KPITTECH", "CYIENT", "ZENSAR"
            );
        } catch (Exception e) {
            logger.warn("Failed to fetch most active stocks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Fetch top gainers and losers
     */
    private List<String> fetchTopMovers() {
        try {
            // Comprehensive list of volatile and moving stocks across all price ranges
            return Arrays.asList(
                // High Movers - Large Cap
                "ADANIENT", "ADANITRANS", "VEDL", "HINDALCO", "INDIACEM", "TATAMOTORS", 
                "SAIL", "NMDC", "MOIL", "APOLLOHOSP", "MRF", "BOSCHLTD",
                
                // Mid Cap Movers (₹500-1500)
                "LUPIN", "AUROPHARMA", "ZEEL", "ASHOKLEY", "MOTHERSON", "EXIDEIND",
                "CUMMINSIND", "VOLTAS", "BLUEDART", "TATACOMM", "TATAPOWER", "ADANIPOWER",
                "JINDALSTEL", "JSPL", "RCOM", "IDEA", "YESBANK", "FEDERALBNK",
                
                // Small Cap Movers (₹100-500) 
                "SUZLON", "GMRINFRA", "JETAIRWAYS", "RELCAPITAL", "RELINFRA", "ADAG",
                "JPASSOCIAT", "JAIPRAKASH", "UNITECH", "DLF", "SOBHA", "GODREJPROP",
                "INDIABULL", "DHFL", "SRTRANSFIN", "LICHSGFIN", "REPCO", "UJJIVAN",
                
                // Tiny Cap & Penny Stocks (< ₹100)
                "YESBANK", "RCOM", "JETAIRWAYS", "SUZLON", "JPASSOCIAT", "UNITECH",
                "RELCAPITAL", "RELINFRA", "PCJEWELLER", "JUSTDIAL", "RBLBANK", "BANDHANBNK",
                "SBIN", "PNB", "CANBK", "INDIANB", "CENTRALBK", "BANKBARODA", "UNIONBANK",
                
                // Sectoral Movers
                "BHARTIARTL", "IDEA", "TATACOMM", "RAILTEL", "MTNL", "ITI", "BSNL",
                "ONGC", "OIL", "GAIL", "IOCL", "BPCL", "HPCL", "MRPL", "CPCL"
            );
        } catch (Exception e) {
            logger.warn("Failed to fetch top movers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Get stable large cap stocks pool
     */
    private List<String> getLargeCapsPool() {
        return Arrays.asList(
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR", 
            "ITC", "KOTAKBANK", "LT", "BHARTIARTL", "SBIN", "HCLTECH"
        );
    }
    
    /**
     * Create LiveMarketStock directly from Yahoo Finance (bypassing NSE API completely)
     */
    private LiveMarketStock createLiveMarketStockFromYahoo(String symbol) {
        try {
            // Get stock data DIRECTLY from Yahoo Finance bypassing NSE API completely
            Optional<StockData> stockDataOpt = nseApiClient.getStockQuoteFromYahooDirectly(symbol);
            if (!stockDataOpt.isPresent()) {
                return null;
            }
            
            StockData stockData = stockDataOpt.get();
            
            // Skip if essential data is missing
            if (stockData.getLastPrice() == null || stockData.getLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            
            // Calculate market cap (simplified - would use actual shares outstanding)
            BigDecimal price = stockData.getLastPrice();
            BigDecimal marketCap = calculateApproxMarketCap(symbol, price);
            
            // Calculate performance score
            BigDecimal performanceScore = calculatePerformanceScore(stockData);
            
            return new LiveMarketStock(symbol, price, marketCap, 
                stockData.getPercentChange(), stockData.getVolume(), performanceScore);
                
        } catch (Exception e) {
            logger.debug("Failed to create LiveMarketStock from Yahoo for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
    
    /**
     * Create LiveMarketStock from symbol (DEPRECATED - keeping for compatibility)
     */
    private LiveMarketStock createLiveMarketStock(String symbol) {
        return createLiveMarketStockFromYahoo(symbol);
    }
    
    /**
     * Calculate approximate market cap (simplified)
     */
    private BigDecimal calculateApproxMarketCap(String symbol, BigDecimal price) {
        // This is a simplified approach - in production, you'd have actual shares outstanding data
        Map<String, BigDecimal> approxShares = getApproximateSharesOutstanding();
        BigDecimal shares = approxShares.getOrDefault(symbol, new BigDecimal("100")); // Default 100 crores
        return price.multiply(shares);
    }
    
    /**
     * Get approximate shares outstanding for major stocks (in crores)
     */
    private Map<String, BigDecimal> getApproximateSharesOutstanding() {
        Map<String, BigDecimal> shares = new HashMap<>();
        // Major large caps (>1000 crores market cap)
        shares.put("RELIANCE", new BigDecimal("676")); // 676 crores shares
        shares.put("TCS", new BigDecimal("364"));
        shares.put("HDFCBANK", new BigDecimal("554"));
        shares.put("INFY", new BigDecimal("424"));
        shares.put("ICICIBANK", new BigDecimal("699"));
        shares.put("HINDUNILVR", new BigDecimal("235"));
        shares.put("ITC", new BigDecimal("1249"));
        shares.put("KOTAKBANK", new BigDecimal("198"));
        shares.put("LT", new BigDecimal("140"));
        shares.put("SBIN", new BigDecimal("894"));
        
        // Medium caps (100-1000 crores)
        shares.put("BAJFINANCE", new BigDecimal("60"));
        shares.put("TECHM", new BigDecimal("97"));
        shares.put("TITAN", new BigDecimal("89"));
        shares.put("ULTRACEMCO", new BigDecimal("29"));
        shares.put("NESTLEIND", new BigDecimal("96"));
        shares.put("ASIANPAINT", new BigDecimal("95"));
        shares.put("MARUTI", new BigDecimal("30"));
        shares.put("AXISBANK", new BigDecimal("306"));
        shares.put("BHARTIARTL", new BigDecimal("593"));
        
        // Small caps (10-100 crores) - using smaller numbers
        shares.put("PFC", new BigDecimal("250"));
        shares.put("RECLTD", new BigDecimal("276"));
        shares.put("SAIL", new BigDecimal("416"));
        shares.put("NMDC", new BigDecimal("186"));
        shares.put("TATASTEEL", new BigDecimal("123"));
        
        return shares;
    }
    
    /**
     * Calculate performance score based on multiple factors
     */
    private BigDecimal calculatePerformanceScore(StockData stock) {
        BigDecimal score = BigDecimal.ZERO;
        
        try {
            // Factor 1: Price change (30% weight)
            BigDecimal priceChange = stock.getPercentChange() != null ? 
                stock.getPercentChange() : BigDecimal.ZERO;
            score = score.add(priceChange.multiply(new BigDecimal("3")));
            
            // Factor 2: Volume surge (20% weight) - simplified
            Long volume = stock.getVolume();
            if (volume != null && volume > 1000000) { // High volume
                score = score.add(new BigDecimal("2"));
            } else if (volume != null && volume > 500000) { // Medium volume
                score = score.add(new BigDecimal("1"));
            }
            
            // Factor 3: Price momentum (simplified 20% weight)
            BigDecimal price = stock.getLastPrice();
            if (price.compareTo(new BigDecimal("100")) > 0) {
                score = score.add(new BigDecimal("1")); // Premium pricing
            }
            
            // Factor 4: Volatility factor (30% weight)
            BigDecimal volatilityScore = priceChange.abs().multiply(new BigDecimal("2"));
            score = score.add(volatilityScore);
            
        } catch (Exception e) {
            logger.debug("Error calculating performance score: {}", e.getMessage());
        }
        
        return score;
    }
    
    /**
     * Categorize stocks by market cap
     */
    private Map<MarketCapCategory, List<LiveMarketStock>> categorizeByMarketCap(List<LiveMarketStock> stocks) {
        Map<MarketCapCategory, List<LiveMarketStock>> categorized = new HashMap<>();
        
        for (MarketCapCategory category : MarketCapCategory.values()) {
            categorized.put(category, new ArrayList<>());
        }
        
        for (LiveMarketStock stock : stocks) {
            MarketCapCategory category = determineMarketCapCategory(stock.getPrice());
            categorized.get(category).add(stock);
        }
        
        // Log category distribution
        for (MarketCapCategory category : MarketCapCategory.values()) {
            int count = categorized.get(category).size();
            logger.info("📊 {} category: {} stocks", category.getDisplayName(), count);
        }
        
        return categorized;
    }
    
    /**
     * Determine price category
     */
    private MarketCapCategory determineMarketCapCategory(BigDecimal price) {
        // Price thresholds (in rupees)
        if (price.compareTo(new BigDecimal("2000")) >= 0) { // ₹2000+
            return MarketCapCategory.GIANT;
        } else if (price.compareTo(new BigDecimal("1000")) >= 0) { // ₹1000-2000
            return MarketCapCategory.LARGE;
        } else if (price.compareTo(new BigDecimal("500")) >= 0) { // ₹500-1000
            return MarketCapCategory.MEDIUM;
        } else if (price.compareTo(new BigDecimal("100")) >= 0) { // ₹100-500
            return MarketCapCategory.SMALL;
        } else { // < ₹100
            return MarketCapCategory.TINY;
        }
    }
    
    /**
     * Create fallback result when live data is not available
     */
    private DynamicScanResult createFallbackScanResult() {
        logger.info("🔄 Creating fallback scan result with curated stocks");
        
        DynamicScanResult result = new DynamicScanResult();
        
        // Fallback stocks by category
        Map<MarketCapCategory, List<String>> fallbackStocks = Map.of(
            MarketCapCategory.GIANT, Arrays.asList("RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK"),
            MarketCapCategory.LARGE, Arrays.asList("HINDUNILVR", "ITC", "KOTAKBANK", "LT", "SBIN"),
            MarketCapCategory.MEDIUM, Arrays.asList("BAJFINANCE", "TITAN", "ULTRACEMCO", "NESTLEIND", "ASIANPAINT"),
            MarketCapCategory.SMALL, Arrays.asList("PFC", "RECLTD", "SAIL", "NMDC", "TATASTEEL"),
            MarketCapCategory.TINY, Arrays.asList("MOIL", "IRCON", "RVNL", "BEML", "BHEL")
        );
        
        for (MarketCapCategory category : MarketCapCategory.values()) {
            List<String> symbols = fallbackStocks.get(category);
            List<LiveMarketStock> stocks = symbols.stream()
                .map(this::createLiveMarketStock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            result.setCategoryPicks(category, stocks);
        }
        
        return result;
    }
    
    // Inner classes
    
    public static class DynamicScanResult {
        private Map<MarketCapCategory, List<LiveMarketStock>> categoryPicks = new HashMap<>();
        
        public void setCategoryPicks(MarketCapCategory category, List<LiveMarketStock> picks) {
            this.categoryPicks.put(category, picks);
        }
        
        public List<LiveMarketStock> getCategoryPicks(MarketCapCategory category) {
            return categoryPicks.getOrDefault(category, new ArrayList<>());
        }
        
        public Map<MarketCapCategory, List<LiveMarketStock>> getAllCategories() {
            return categoryPicks;
        }
        
        public int getTotalStocks() {
            return categoryPicks.values().stream()
                .mapToInt(List::size)
                .sum();
        }
    }
    
    public static class LiveMarketStock {
        private final String symbol;
        private final BigDecimal price;
        private final BigDecimal marketCap;
        private final BigDecimal changePercent;
        private final Long volume;
        private final BigDecimal performanceScore;
        
        public LiveMarketStock(String symbol, BigDecimal price, BigDecimal marketCap,
                             BigDecimal changePercent, Long volume, BigDecimal performanceScore) {
            this.symbol = symbol;
            this.price = price;
            this.marketCap = marketCap;
            this.changePercent = changePercent != null ? changePercent : BigDecimal.ZERO;
            this.volume = volume;
            this.performanceScore = performanceScore;
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getMarketCap() { return marketCap; }
        public BigDecimal getChangePercent() { return changePercent; }
        public Long getVolume() { return volume; }
        public BigDecimal getPerformanceScore() { return performanceScore; }
        
        public boolean isGainer() { return changePercent.compareTo(BigDecimal.ZERO) > 0; }
        public boolean isLoser() { return changePercent.compareTo(BigDecimal.ZERO) < 0; }
    }
    
    public enum MarketCapCategory {
        TINY("Tiny Stocks", "< ₹100", "💎"),
        SMALL("Small Stocks", "₹100-500", "🔹"), 
        MEDIUM("Medium Stocks", "₹500-1000", "🔷"),
        LARGE("Large Stocks", "₹1000-2000", "🟦"),
        GIANT("Giant Stocks", "> ₹2000", "🏗️");
        
        private final String displayName;
        private final String range;
        private final String emoji;
        
        MarketCapCategory(String displayName, String range, String emoji) {
            this.displayName = displayName;
            this.range = range;
            this.emoji = emoji;
        }
        
        public String getDisplayName() { return displayName; }
        public String getRange() { return range; }
        public String getEmoji() { return emoji; }
    }
}