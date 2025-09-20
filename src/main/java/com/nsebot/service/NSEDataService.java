package com.nsebot.service;

import com.nsebot.dto.StockData;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for NSE market data operations
 */
public interface NSEDataService {
    
    /**
     * Fetch current stock data for a specific symbol
     * @param symbol Stock symbol (e.g., "RELIANCE")
     * @return Stock data if available
     */
    Optional<StockData> getStockData(String symbol);
    
    /**
     * Fetch stock data for multiple symbols
     * @param symbols List of stock symbols
     * @return List of stock data
     */
    List<StockData> getMultipleStockData(List<String> symbols);
    
    /**
     * Get list of Nifty 100 stock symbols
     * @return List of stock symbols in Nifty 100
     */
    List<String> getNifty100Symbols();
    
    /**
     * Fetch historical data for technical analysis
     * @param symbol Stock symbol
     * @param days Number of days of historical data
     * @return List of historical stock data
     */
    List<StockData> getHistoricalData(String symbol, int days);
}