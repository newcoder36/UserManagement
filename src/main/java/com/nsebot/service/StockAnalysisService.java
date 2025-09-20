package com.nsebot.service;

/**
 * Service interface for stock analysis operations
 */
public interface StockAnalysisService {
    
    /**
     * Performs market scan of top 100 Nifty stocks
     * @return Formatted string with top 15-20 recommendations
     */
    String performMarketScan();
    
    /**
     * Analyzes a specific stock
     * @param symbol Stock symbol to analyze
     * @return Detailed analysis with recommendations
     */
    String analyzeStock(String symbol);
}