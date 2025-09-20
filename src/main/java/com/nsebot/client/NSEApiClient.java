package com.nsebot.client;

import com.nsebot.config.NSEApiConfig;
import com.nsebot.config.YahooFinanceConfig;
import com.nsebot.config.SmartWebClientConfig;
import com.nsebot.dto.StockData;
import com.nsebot.service.DataSourceStatsService;
import com.nsebot.service.RequestThrottleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Client for interacting with NSE API
 */
@Component
public class NSEApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(NSEApiClient.class);
    
    private final WebClient webClient;
    private final WebClient smartNseWebClient;
    private final WebClient smartYahooWebClient;
    private final NSEApiConfig apiConfig;
    private final YahooFinanceConfig yahooConfig;
    private final ObjectMapper objectMapper;
    private final DataSourceStatsService statsService;
    private final RequestThrottleService throttleService;
    private final Map<String, String> sessionCookies = new ConcurrentHashMap<>();
    private volatile long lastSessionRefresh = 0;
    private static final long SESSION_REFRESH_INTERVAL = 30 * 60 * 1000; // 30 minutes
    
    // Simple Circuit Breaker for Yahoo Finance
    private volatile int yahooFailureCount = 0;
    private volatile long lastYahooFailureTime = 0;
    private static final int YAHOO_CIRCUIT_BREAK_THRESHOLD = 5;
    private static final long YAHOO_CIRCUIT_BREAK_TIMEOUT = 30 * 1000; // 30 seconds
    
    // NSE API endpoints
    private static final String QUOTE_ENDPOINT = "/api/quote-equity";
    private static final String MARKET_DATA_ENDPOINT = "/api/marketStatus";
    private static final String INDICES_ENDPOINT = "/api/allIndices";
    private static final String HOME_PAGE = "https://www.nseindia.com";
    
    // Yahoo Finance API (more reliable alternative)
    private static final String YAHOO_FINANCE_BASE = "https://query1.finance.yahoo.com/v8/finance/chart";
    private final WebClient yahooWebClient;
    
    @Autowired
    public NSEApiClient(NSEApiConfig apiConfig, YahooFinanceConfig yahooConfig, ObjectMapper objectMapper, 
                       DataSourceStatsService statsService, RequestThrottleService throttleService,
                       @Qualifier("smartNseWebClient") WebClient smartNseWebClient,
                       @Qualifier("smartYahooWebClient") WebClient smartYahooWebClient) {
        this.apiConfig = apiConfig;
        this.yahooConfig = yahooConfig;
        this.objectMapper = objectMapper;
        this.statsService = statsService;
        this.throttleService = throttleService;
        this.smartNseWebClient = smartNseWebClient;
        this.smartYahooWebClient = smartYahooWebClient;
        
        this.webClient = WebClient.builder()
                .baseUrl(apiConfig.getBaseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .defaultHeader("Accept-Encoding", "gzip, deflate, br, identity")
                .defaultHeader("Connection", "keep-alive")
                .defaultHeader("Referer", "https://www.nseindia.com/")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .defaultHeader("Cache-Control", "no-cache")
                .defaultHeader("Pragma", "no-cache")
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                    // Enable automatic decompression
                    configurer.defaultCodecs().enableLoggingRequestDetails(true);
                })
                .build();
        
        // Initialize Yahoo Finance WebClient
        this.yahooWebClient = WebClient.builder()
                .baseUrl(YAHOO_FINANCE_BASE)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    /**
     * Fetch stock quote from NSE API
     * @param symbol Stock symbol
     * @return Optional containing stock data if successful
     */
    public Optional<StockData> getStockQuote(String symbol) {
        try {
            // Smart throttling to avoid being detected as bot
            throttleService.waitForNseRequest(symbol);
            
            // Ensure we have a valid session with smart session management
            if (!ensureValidSmartSession()) {
                logger.warn("Could not establish valid NSE session for symbol: {}, trying Yahoo Finance", symbol);
                statsService.recordNseFailure(symbol, "Session establishment failed");
                return getStockQuoteFromYahooSmart(symbol);
            }
            
            logger.info("🔍 Attempting NSE API call for symbol: {} with smart anti-bot features", symbol);
            
            // Add random jitter to avoid predictable patterns
            throttleService.addRandomJitter();
            
            String response = smartNseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(QUOTE_ENDPOINT)
                            .queryParam("symbol", symbol)
                            .build())
                    .headers(headers -> {
                        // Add session cookies
                        String cookieString = sessionCookies.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("");
                        if (!cookieString.isEmpty()) {
                            headers.add("Cookie", cookieString);
                        }
                        // Smart headers with randomization
                        headers.add("Accept", SmartWebClientConfig.getRandomAcceptHeader());
                        headers.add("Accept-Charset", "utf-8");
                        headers.add("Referer", "https://www.nseindia.com/");
                        // Randomize User-Agent for each request
                        headers.set("User-Agent", SmartWebClientConfig.getRandomUserAgent());
                    })
                    .exchangeToMono(clientResponse -> {
                        logger.debug("NSE API response status: {} for symbol: {}", clientResponse.statusCode(), symbol);
                        
                        // Check content type
                        String contentType = clientResponse.headers().contentType()
                            .map(mediaType -> mediaType.toString())
                            .orElse("unknown");
                        logger.debug("NSE API response content type: {} for symbol: {}", contentType, symbol);
                        
                        return clientResponse.bodyToMono(String.class);
                    })
                    .timeout(Duration.ofMillis(apiConfig.getTimeout()))
                    .block();
            
            if (response != null && !response.trim().isEmpty()) {
                StockData stockData = parseStockData(response, symbol);
                if (stockData != null) {
                    statsService.recordNseSuccess(symbol);
                    return Optional.of(stockData);
                } else {
                    // parseStockData failed, try Yahoo Finance
                    statsService.recordNseFailure(symbol, "Binary/corrupted response detected");
                    logger.info("NSE data parsing failed for {}, trying Yahoo Finance", symbol);
                    return getStockQuoteFromYahooSmart(symbol);
                }
            }
            
        } catch (WebClientException e) {
            logger.error("Error fetching quote for symbol {}: {}", symbol, e.getMessage());
            // Clear session cookies on auth failure
            if (e.getMessage().contains("401")) {
                sessionCookies.clear();
                lastSessionRefresh = 0;
                statsService.recordNseFailure(symbol, "401 Unauthorized");
            } else {
                statsService.recordNseFailure(symbol, "WebClient error: " + e.getMessage());
            }
            // Try Yahoo Finance API as fallback
            logger.info("Attempting to fetch {} from Yahoo Finance API", symbol);
            return getStockQuoteFromYahooSmart(symbol);
        } catch (Exception e) {
            logger.error("Unexpected error fetching quote for symbol {}", symbol, e);
            statsService.recordNseFailure(symbol, "Unexpected error: " + e.getMessage());
            // Try Yahoo Finance API as fallback
            logger.info("Attempting to fetch {} from Yahoo Finance API", symbol);
            return getStockQuoteFromYahooSmart(symbol);
        }
        
        // If NSE API returns empty response, try Yahoo Finance
        logger.info("NSE API returned empty response for {}, trying Yahoo Finance", symbol);
        return getStockQuoteFromYahooSmart(symbol);
    }
    
    /**
     * Ensure we have a valid NSE session with cookies
     */
    /**
     * Smart session management with anti-bot features
     */
    private boolean ensureValidSmartSession() {
        long currentTime = System.currentTimeMillis();
        
        // Check if we need to refresh session
        if (sessionCookies.isEmpty() || (currentTime - lastSessionRefresh) > SESSION_REFRESH_INTERVAL) {
            return initializeSmartSession();
        }
        
        return true;
    }
    
    private boolean ensureValidSession() {
        long currentTime = System.currentTimeMillis();
        
        // Check if we need to refresh session
        if (sessionCookies.isEmpty() || (currentTime - lastSessionRefresh) > SESSION_REFRESH_INTERVAL) {
            return initializeSession();
        }
        
        return true;
    }
    
    /**
     * Smart session initialization with anti-bot features
     */
    private boolean initializeSmartSession() {
        try {
            logger.info("🔐 Initializing smart NSE session with anti-bot features...");
            
            // Add throttling before session request
            throttleService.waitForNseRequest("SESSION_INIT");
            throttleService.addRandomJitter();
            
            // Visit NSE home page to get session cookies with realistic browser simulation
            smartNseWebClient.get()
                    .uri("/")
                    .headers(headers -> {
                        headers.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
                        headers.add("Accept-Language", "en-US,en;q=0.9");
                        headers.add("Cache-Control", "no-cache");
                        headers.add("Pragma", "no-cache");
                        headers.add("Sec-Fetch-Dest", "document");
                        headers.add("Sec-Fetch-Mode", "navigate");
                        headers.add("Sec-Fetch-Site", "none");
                        headers.add("Sec-Fetch-User", "?1");
                        headers.add("Upgrade-Insecure-Requests", "1");
                    })
                    .exchangeToMono(response -> {
                        // Extract cookies from response
                        response.headers().asHttpHeaders().forEach((name, values) -> {
                            if ("Set-Cookie".equalsIgnoreCase(name)) {
                                for (String cookieHeader : values) {
                                    String[] parts = cookieHeader.split(";")[0].split("=", 2);
                                    if (parts.length == 2) {
                                        sessionCookies.put(parts[0].trim(), parts[1].trim());
                                        logger.debug("🍪 Stored smart session cookie: {}", parts[0]);
                                    }
                                }
                            }
                        });
                        return response.bodyToMono(String.class);
                    })
                    .block();
            
            lastSessionRefresh = System.currentTimeMillis();
            boolean success = !sessionCookies.isEmpty();
            
            if (success) {
                logger.info("✅ Smart NSE session established successfully with {} cookies", sessionCookies.size());
            } else {
                logger.warn("⚠️ Smart NSE session establishment failed - no cookies received");
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("❌ Failed to initialize smart NSE session: {}", e.getMessage());
            sessionCookies.clear();
            return false;
        }
    }

    /**
     * Initialize NSE session by visiting home page
     */
    private boolean initializeSession() {
        try {
            logger.info("Initializing NSE session...");
            
            // Visit NSE home page to get session cookies
            webClient.get()
                    .uri(HOME_PAGE)
                    .exchangeToMono(response -> {
                        // Extract cookies from response
                        response.headers().asHttpHeaders().forEach((name, values) -> {
                            if ("Set-Cookie".equalsIgnoreCase(name)) {
                                for (String cookieHeader : values) {
                                    String[] parts = cookieHeader.split(";")[0].split("=", 2);
                                    if (parts.length == 2) {
                                        sessionCookies.put(parts[0].trim(), parts[1].trim());
                                        logger.debug("Stored cookie: {}", parts[0]);
                                    }
                                }
                            }
                        });
                        return response.bodyToMono(String.class);
                    })
                    .timeout(Duration.ofMillis(apiConfig.getTimeout()))
                    .block();
            
            lastSessionRefresh = System.currentTimeMillis();
            logger.info("NSE session initialized with {} cookies", sessionCookies.size());
            
            return !sessionCookies.isEmpty();
            
        } catch (Exception e) {
            logger.error("Failed to initialize NSE session: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Create mock stock data for development purposes
     */
    private Optional<StockData> createMockStockData(String symbol) {
        logger.info("Creating mock data for symbol: {}", symbol);
        
        StockData mockData = new StockData();
        mockData.setSymbol(symbol);
        mockData.setCompanyName("Mock Company for " + symbol);
        
        // Generate some realistic mock values
        BigDecimal basePrice = new BigDecimal("1000.00");
        double randomFactor = 0.8 + (Math.random() * 0.4); // Random between 0.8 and 1.2
        
        mockData.setLastPrice(basePrice.multiply(new BigDecimal(randomFactor)));
        mockData.setChange(new BigDecimal(String.valueOf((Math.random() - 0.5) * 50))); // -25 to +25
        mockData.setPercentChange(new BigDecimal(String.valueOf((Math.random() - 0.5) * 5))); // -2.5% to +2.5%
        mockData.setOpenPrice(basePrice.multiply(new BigDecimal(randomFactor * 0.98)));
        mockData.setDayHigh(basePrice.multiply(new BigDecimal(randomFactor * 1.05)));
        mockData.setDayLow(basePrice.multiply(new BigDecimal(randomFactor * 0.95)));
        mockData.setPreviousClose(basePrice.multiply(new BigDecimal(randomFactor * 0.99)));
        mockData.setVolume((long) (100000 + Math.random() * 900000));
        
        return Optional.of(mockData);
    }
    
    /**
     * Check if NSE market is open
     * @return true if market is open
     */
    public boolean isMarketOpen() {
        try {
            String response = webClient.get()
                    .uri(MARKET_DATA_ENDPOINT)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(apiConfig.getTimeout()))
                    .block();
            
            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode marketStatus = jsonNode.get("marketState");
                return marketStatus != null && "Market is Open".equals(marketStatus.asText());
            }
            
        } catch (Exception e) {
            logger.error("Error checking market status", e);
        }
        
        return false; // Default to closed if unable to determine
    }
    
    /**
     * Parse NSE API response into StockData object
     */
    private StockData parseStockData(String jsonResponse, String symbol) {
        try {
            // Check for binary/corrupted response by looking for non-printable or suspicious characters
            boolean hasBinaryData = jsonResponse.chars()
                    .anyMatch(c -> c < 32 && c != 9 && c != 10 && c != 13) || // Non-printable chars
                    jsonResponse.chars().anyMatch(c -> c > 127) || // Extended ASCII
                    containsCompressionIndicators(jsonResponse);
            
            if (hasBinaryData) {
                logger.warn("Received binary/corrupted response for symbol {}, likely compressed data that wasn't decompressed properly", symbol);
                throw new RuntimeException("Invalid response - received binary/corrupted data");
            }
            
            // Log first few characters to debug JSON parsing issues (only printable chars)
            String previewResponse = jsonResponse.length() > 100 ? 
                jsonResponse.substring(0, 100).replaceAll("[\\p{Cntrl}]", "?") : 
                jsonResponse.replaceAll("[\\p{Cntrl}]", "?");
            logger.debug("Response preview for {}: {}", symbol, previewResponse);
            
            // Check if response looks like HTML (error page)
            if (jsonResponse.trim().startsWith("<")) {
                logger.warn("Received HTML response instead of JSON for symbol {}, likely an error page", symbol);
                throw new RuntimeException("Invalid JSON response - received HTML error page");
            }
            
            // Check if response starts with valid JSON character
            char firstChar = jsonResponse.trim().charAt(0);
            if (firstChar != '{' && firstChar != '[') {
                logger.warn("Response for {} doesn't start with valid JSON character: {}", symbol, firstChar);
                throw new RuntimeException("Invalid JSON response - doesn't start with { or [");
            }
            
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            StockData stockData = new StockData();
            stockData.setSymbol(symbol);
            
            // Extract price information from NSE response format
            if (rootNode.has("priceInfo")) {
                JsonNode priceInfo = rootNode.get("priceInfo");
                
                if (priceInfo.has("lastPrice")) {
                    stockData.setLastPrice(new BigDecimal(priceInfo.get("lastPrice").asText()));
                }
                if (priceInfo.has("change")) {
                    stockData.setChange(new BigDecimal(priceInfo.get("change").asText()));
                }
                if (priceInfo.has("pChange")) {
                    stockData.setPercentChange(new BigDecimal(priceInfo.get("pChange").asText()));
                }
                if (priceInfo.has("open")) {
                    stockData.setOpenPrice(new BigDecimal(priceInfo.get("open").asText()));
                }
                if (priceInfo.has("intraDayHighLow")) {
                    JsonNode highLow = priceInfo.get("intraDayHighLow");
                    if (highLow.has("max")) {
                        stockData.setDayHigh(new BigDecimal(highLow.get("max").asText()));
                    }
                    if (highLow.has("min")) {
                        stockData.setDayLow(new BigDecimal(highLow.get("min").asText()));
                    }
                }
                if (priceInfo.has("previousClose")) {
                    stockData.setPreviousClose(new BigDecimal(priceInfo.get("previousClose").asText()));
                }
            }
            
            // Extract company information
            if (rootNode.has("info") && rootNode.get("info").has("companyName")) {
                stockData.setCompanyName(rootNode.get("info").get("companyName").asText());
            }
            
            // Extract volume information
            if (rootNode.has("securityWiseDP") && rootNode.get("securityWiseDP").has("quantityTraded")) {
                stockData.setVolume(rootNode.get("securityWiseDP").get("quantityTraded").asLong());
            }
            
            return stockData;
            
        } catch (Exception e) {
            logger.error("Error parsing stock data for symbol {}, will try Yahoo Finance", symbol, e);
            // Return null to trigger Yahoo Finance fallback
            return null;
        }
    }
    
    /**
     * Fetch stock data from Yahoo Finance API
     * @param symbol NSE symbol (will be converted to Yahoo Finance format)
     * @return Optional containing stock data if successful
     */
    private Optional<StockData> getStockQuoteFromYahoo(String symbol) {
        try {
            // Convert NSE symbol to Yahoo Finance format (add .NS suffix)
            String yahooSymbol = convertToYahooSymbol(symbol);
            
            logger.info("Attempting to fetch {} from Yahoo Finance API as {}", symbol, yahooSymbol);
            
            String response = yahooWebClient.get()
                    .uri("/{symbol}", yahooSymbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(yahooConfig.getTimeout()))
                    .onErrorResume(Exception.class, ex -> {
                        logger.warn("❌ Basic Yahoo Finance call failed for {}: {}", symbol, ex.getClass().getSimpleName());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .block();
            
            if (response != null && !response.trim().isEmpty()) {
                StockData stockData = parseYahooFinanceData(response, symbol);
                if (stockData != null) {
                    logger.info("Successfully fetched live data from Yahoo Finance for {}", symbol);
                    statsService.recordYahooSuccess(symbol);
                    return Optional.of(stockData);
                }
            }
            
        } catch (Exception e) {
            logger.warn("Failed to fetch data from Yahoo Finance for {}: {}", symbol, e.getMessage());
            statsService.recordYahooFailure(symbol, e.getMessage());
        }
        
        // Final fallback to mock data only if both APIs fail
        logger.warn("Both NSE and Yahoo Finance APIs failed for {}, using mock data as last resort", symbol);
        statsService.recordMockData(symbol);
        return createMockStockData(symbol);
    }
    
    /**
     * PUBLIC method to get stock quote directly from Yahoo Finance (bypassing NSE completely)
     * @param symbol NSE symbol (will be converted to Yahoo Finance format)
     * @return Optional containing stock data if successful
     */
    public Optional<StockData> getStockQuoteFromYahooDirectly(String symbol) {
        logger.info("🚀 Bypassing NSE API - Calling Yahoo Finance directly for {}", symbol);
        return getStockQuoteFromYahooSmart(symbol);
    }
    
    /**
     * Smart Yahoo Finance API call with rate limiting and retry logic
     * @param symbol NSE symbol (will be converted to Yahoo Finance format)
     * @return Optional containing stock data if successful
     */
    private Optional<StockData> getStockQuoteFromYahooSmart(String symbol) {
        // Check circuit breaker
        if (isYahooCircuitOpen()) {
            logger.warn("⚠️ Yahoo Finance circuit breaker is OPEN for {}, returning mock data", symbol);
            statsService.recordMockData(symbol);
            return createMockStockData(symbol);
        }
        
        try {
            // Convert NSE symbol to Yahoo Finance format (add .NS suffix)
            String yahooSymbol = convertToYahooSymbol(symbol);
            
            logger.info("🔄 Attempting to fetch {} from smart Yahoo Finance API as {}", symbol, yahooSymbol);
            
            // Smart throttling to avoid being rate limited
            throttleService.waitForYahooRequest(symbol);
            
            // Add random jitter to avoid predictable patterns
            throttleService.addRandomJitter();
            
            String response = smartYahooWebClient.get()
                    .uri("/{symbol}", yahooSymbol)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        if (clientResponse.statusCode().value() == 429) {
                            logger.warn("⚠️ Yahoo Finance rate limit hit for {}, will retry with backoff", symbol);
                            return Mono.error(new RuntimeException("Rate limited - 429"));
                        }
                        return Mono.error(new RuntimeException("Client error: " + clientResponse.statusCode()));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(yahooConfig.getTimeout()))
                    .onErrorResume(Exception.class, ex -> {
                        logger.warn("❌ Yahoo Finance error for {}: {} - Returning empty", symbol, ex.getClass().getSimpleName());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .retryWhen(Retry.backoff(yahooConfig.getRetryAttempts(), Duration.ofSeconds(1))
                        .filter(throwable -> throwable.getMessage().contains("Rate limited")))
                    .block();
            
            if (response != null && !response.trim().isEmpty()) {
                StockData stockData = parseYahooFinanceData(response, symbol);
                if (stockData != null) {
                    logger.info("✅ Successfully fetched live data from smart Yahoo Finance for {}", symbol);
                    recordYahooSuccess(); // Reset circuit breaker on success
                    statsService.recordYahooSuccess(symbol);
                    return Optional.of(stockData);
                }
            }
            
        } catch (Exception e) {
            logger.warn("❌ Failed to fetch data from smart Yahoo Finance for {}: {}", symbol, e.getMessage());
            recordYahooFailure();
            statsService.recordYahooFailure(symbol, e.getMessage());
            
            // If rate limited, try with longer delay
            if (e.getMessage().contains("Rate limited") || e.getMessage().contains("429")) {
                try {
                    logger.info("🔄 Retrying {} with extended delay due to rate limiting", symbol);
                    Thread.sleep(5000); // 5 second delay for rate limit recovery
                    
                    String response = smartYahooWebClient.get()
                            .uri("/{symbol}", convertToYahooSymbol(symbol))
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(yahooConfig.getTimeout()))
                            .onErrorResume(Exception.class, ex -> {
                                logger.warn("❌ Yahoo Finance retry failed for {}: {}", symbol, ex.getClass().getSimpleName());
                                return reactor.core.publisher.Mono.empty();
                            })
                            .block();
                    
                    if (response != null && !response.trim().isEmpty()) {
                        StockData stockData = parseYahooFinanceData(response, symbol);
                        if (stockData != null) {
                            logger.info("✅ Successfully fetched data from Yahoo Finance on retry for {}", symbol);
                            statsService.recordYahooSuccess(symbol);
                            return Optional.of(stockData);
                        }
                    }
                } catch (Exception retryEx) {
                    logger.error("❌ Retry also failed for Yahoo Finance {}: {}", symbol, retryEx.getMessage());
                    statsService.recordYahooFailure(symbol, "Retry failed: " + retryEx.getMessage());
                }
            }
        }
        
        // Final fallback to mock data only if both APIs fail
        logger.warn("⚠️ Both NSE and Yahoo Finance APIs failed for {}, using mock data as last resort", symbol);
        statsService.recordMockData(symbol);
        return createMockStockData(symbol);
    }
    
    /**
     * Convert NSE symbol to Yahoo Finance symbol format
     * @param nseSymbol NSE symbol (e.g., "RELIANCE")
     * @return Yahoo Finance symbol (e.g., "RELIANCE.NS")
     */
    private String convertToYahooSymbol(String nseSymbol) {
        // Most NSE stocks are traded with .NS suffix on Yahoo Finance
        // Special cases can be handled here if needed
        return nseSymbol.toUpperCase() + ".NS";
    }
    
    /**
     * Parse Yahoo Finance API response into StockData object
     */
    private StockData parseYahooFinanceData(String jsonResponse, String originalSymbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Yahoo Finance API structure: chart -> result[0] -> meta and indicators
            JsonNode chartNode = rootNode.get("chart");
            if (chartNode == null || !chartNode.has("result") || chartNode.get("result").size() == 0) {
                logger.warn("Invalid Yahoo Finance response structure for {}", originalSymbol);
                return null;
            }
            
            JsonNode resultNode = chartNode.get("result").get(0);
            JsonNode metaNode = resultNode.get("meta");
            
            StockData stockData = new StockData();
            stockData.setSymbol(originalSymbol);
            
            // Extract current price and basic info with validation
            if (metaNode.has("regularMarketPrice")) {
                BigDecimal lastPrice = new BigDecimal(metaNode.get("regularMarketPrice").asText());
                stockData.setLastPrice(lastPrice);
                logger.debug("💰 {} - Current Price: ₹{}", originalSymbol, lastPrice);
            }
            
            if (metaNode.has("previousClose")) {
                BigDecimal previousClose = new BigDecimal(metaNode.get("previousClose").asText());
                stockData.setPreviousClose(previousClose);
                logger.debug("📊 {} - Previous Close: ₹{}", originalSymbol, previousClose);
                
                // Calculate change and percent change
                if (stockData.getLastPrice() != null) {
                    BigDecimal change = stockData.getLastPrice().subtract(previousClose);
                    stockData.setChange(change);
                    
                    if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal percentChange = change.divide(previousClose, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
                        stockData.setPercentChange(percentChange);
                        logger.debug("📈 {} - Change: ₹{} ({}%)", originalSymbol, change, percentChange);
                    }
                }
            }
            
            // Extract OHLC data
            if (metaNode.has("regularMarketOpen")) {
                stockData.setOpenPrice(new BigDecimal(metaNode.get("regularMarketOpen").asText()));
            }
            if (metaNode.has("regularMarketDayHigh")) {
                stockData.setDayHigh(new BigDecimal(metaNode.get("regularMarketDayHigh").asText()));
            }
            if (metaNode.has("regularMarketDayLow")) {
                stockData.setDayLow(new BigDecimal(metaNode.get("regularMarketDayLow").asText()));
            }
            if (metaNode.has("regularMarketVolume")) {
                stockData.setVolume(metaNode.get("regularMarketVolume").asLong());
            }
            
            // Company name
            if (metaNode.has("longName")) {
                stockData.setCompanyName(metaNode.get("longName").asText());
            } else if (metaNode.has("shortName")) {
                stockData.setCompanyName(metaNode.get("shortName").asText());
            } else {
                stockData.setCompanyName(getCompanyNameFromSymbol(originalSymbol));
            }
            
            // Final validation - ensure we have basic price data
            if (stockData.getLastPrice() == null || stockData.getLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
                logger.warn("⚠️ {} - Invalid or missing price data from Yahoo Finance", originalSymbol);
                return null;
            }
            
            logger.info("✅ {} - Successfully parsed Yahoo Finance data - Price: ₹{}, Change: {}%", 
                originalSymbol, stockData.getLastPrice(), 
                stockData.getPercentChange() != null ? stockData.getPercentChange().setScale(2, RoundingMode.HALF_UP) : "N/A");
            
            return stockData;
            
        } catch (Exception e) {
            logger.error("Error parsing Yahoo Finance data for symbol {}", originalSymbol, e);
            return null;
        }
    }
    
    /**
     * Get company name from symbol mapping
     */
    private String getCompanyNameFromSymbol(String symbol) {
        return switch (symbol.toUpperCase()) {
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
            case "BHARTIARTL" -> "Bharti Airtel Limited";
            case "ASIANPAINT" -> "Asian Paints Limited";
            case "MARUTI" -> "Maruti Suzuki India Limited";
            case "BAJFINANCE" -> "Bajaj Finance Limited";
            case "NESTLEIND" -> "Nestle India Limited";
            case "HCLTECH" -> "HCL Technologies Limited";
            case "WIPRO" -> "Wipro Limited";
            case "ULTRACEMCO" -> "UltraTech Cement Limited";
            case "SUNPHARMA" -> "Sun Pharmaceutical Industries Limited";
            case "TITAN" -> "Titan Company Limited";
            default -> symbol + " Limited";
        };
    }
    
    /**
     * Check if response contains indicators of compressed/binary data
     */
    private boolean containsCompressionIndicators(String response) {
        if (response.length() < 10) return false;
        
        // Check for patterns that suggest compressed data
        String firstFewChars = response.substring(0, Math.min(10, response.length()));
        
        // Common compression signatures and patterns we're seeing from NSE
        return firstFewChars.startsWith("PK") || // ZIP
               firstFewChars.startsWith("\u001f\u008b") || // GZIP
               firstFewChars.contains("�") || // Replacement character indicating encoding issues
               firstFewChars.matches(".*[\\u0000-\\u001f\\u007f-\\u00ff]+.*") || // Control chars or extended ASCII
               (firstFewChars.startsWith("P") && response.contains("�")) || // Pattern we see from NSE
               (response.contains("`") && response.contains("�")) || // Another pattern from NSE
               (response.length() > 50 && response.chars().limit(50)
                   .mapToDouble(c -> c > 127 ? 1.0 : 0.0).average().orElse(0) > 0.2); // >20% high-ASCII chars
    }
    
    /**
     * Get predefined list of Nifty 100 stock symbols
     * TODO: This should ideally fetch from NSE API dynamically
     */
    public List<String> getNifty100Symbols() {
        return List.of(
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "HDFC", "ICICIBANK", "KOTAKBANK",
            "HINDUNILVR", "SBIN", "BHARTIARTL", "ITC", "ASIANPAINT", "LT", "AXISBANK",
            "MARUTI", "DMART", "SUNPHARMA", "TITAN", "ULTRACEMCO", "NESTLEIND",
            "BAJFINANCE", "WIPRO", "M&M", "NTPC", "TECHM", "HCLTECH", "POWERGRID",
            "TATASTEEL", "ADANIENT", "ONGC", "COALINDIA", "IOC", "GRASIM", "SBILIFE",
            "BAJAJ-AUTO", "HDFCLIFE", "BRITANNIA", "JSWSTEEL", "CIPLA", "DRREDDY",
            "DIVISLAB", "EICHERMOT", "GODREJCP", "HEROMOTOCO", "INDUSINDBK", "SHREECEM",
            "TATAMOTORS", "UPL", "APOLLOHOSP", "BAJAJFINSV", "BPCL", "HINDALCO",
            "PIDILITIND", "TATACONSUM", "DABUR", "ADANIPORTS", "SIEMENS", "GAIL",
            "MARICO", "LUPIN", "COLPAL", "MCDOWELL-N", "ACC", "VEDL", "BANDHANBNK",
            "BIOCON", "CADILAHC", "CONCOR", "HAVELLS", "ICICIPRULI", "MOTHERSUMI",
            "MRF", "NAUKRI", "OFSS", "PEL", "PETRONET", "PFC", "RECLTD", "SAIL",
            "TORNTPHARM", "TORNTPOWER", "TRENT", "UBL", "VOLTAS", "WHIRLPOOL",
            "AMBUJACEM", "ASHOKLEY", "BANKBARODA", "BERGEPAINT", "CANBK", "CUMMINSIND",
            "DLF", "GICRE", "HDFCAMC", "IBULHSGFIN", "L&TFH", "LICHSGFIN", "NMDC", 
            "PAGEIND", "PNB", "RAMCOCEM", "SRTRANSFIN", "TATACHEM", "TATAPOWER",
            "INDIGO", "BAJAJHLDNG", "JUBLFOOD", "MANAPPURAM", "MUTHOOTFIN", "INDIANB"
        );
    }
    
    /**
     * Check if Yahoo Finance circuit breaker should be open (preventing calls)
     */
    private boolean isYahooCircuitOpen() {
        long currentTime = System.currentTimeMillis();
        
        // If we've exceeded failure threshold and haven't waited long enough, circuit is open
        if (yahooFailureCount >= YAHOO_CIRCUIT_BREAK_THRESHOLD) {
            if ((currentTime - lastYahooFailureTime) < YAHOO_CIRCUIT_BREAK_TIMEOUT) {
                return true; // Circuit is open
            } else {
                // Timeout has passed, reset and allow half-open state
                yahooFailureCount = 0;
                logger.info("🔄 Yahoo Finance circuit breaker transitioning to HALF-OPEN state");
                return false;
            }
        }
        
        return false; // Circuit is closed
    }
    
    /**
     * Record Yahoo Finance failure for circuit breaker
     */
    private void recordYahooFailure() {
        yahooFailureCount++;
        lastYahooFailureTime = System.currentTimeMillis();
        
        if (yahooFailureCount >= YAHOO_CIRCUIT_BREAK_THRESHOLD) {
            logger.warn("🔥 Yahoo Finance circuit breaker OPENED after {} failures", yahooFailureCount);
        }
    }
    
    /**
     * Record Yahoo Finance success - resets circuit breaker
     */
    private void recordYahooSuccess() {
        if (yahooFailureCount > 0) {
            logger.info("✅ Yahoo Finance circuit breaker CLOSED - successful call after {} failures", yahooFailureCount);
        }
        yahooFailureCount = 0;
    }
}