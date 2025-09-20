package com.nsebot.util;

import com.nsebot.dto.StockData;
import com.nsebot.entity.StockPrice;
import org.springframework.stereotype.Component;

/**
 * Utility class for mapping between StockData DTO and StockPrice entity
 */
@Component
public class StockDataMapper {
    
    /**
     * Convert StockData DTO to StockPrice entity
     */
    public StockPrice toEntity(StockData stockData) {
        if (stockData == null) {
            return null;
        }
        
        StockPrice stockPrice = new StockPrice();
        stockPrice.setSymbol(stockData.getSymbol());
        stockPrice.setCompanyName(stockData.getCompanyName());
        stockPrice.setLastPrice(stockData.getLastPrice());
        stockPrice.setChange(stockData.getChange());
        stockPrice.setPercentChange(stockData.getPercentChange());
        stockPrice.setOpenPrice(stockData.getOpenPrice());
        stockPrice.setDayHigh(stockData.getDayHigh());
        stockPrice.setDayLow(stockData.getDayLow());
        stockPrice.setPreviousClose(stockData.getPreviousClose());
        stockPrice.setVolume(stockData.getVolume());
        stockPrice.setTurnover(stockData.getTurnover());
        stockPrice.setTimestamp(stockData.getTimestamp());
        
        return stockPrice;
    }
    
    /**
     * Convert StockPrice entity to StockData DTO
     */
    public StockData toDto(StockPrice stockPrice) {
        if (stockPrice == null) {
            return null;
        }
        
        StockData stockData = new StockData();
        stockData.setSymbol(stockPrice.getSymbol());
        stockData.setCompanyName(stockPrice.getCompanyName());
        stockData.setLastPrice(stockPrice.getLastPrice());
        stockData.setChange(stockPrice.getChange());
        stockData.setPercentChange(stockPrice.getPercentChange());
        stockData.setOpenPrice(stockPrice.getOpenPrice());
        stockData.setDayHigh(stockPrice.getDayHigh());
        stockData.setDayLow(stockPrice.getDayLow());
        stockData.setPreviousClose(stockPrice.getPreviousClose());
        stockData.setVolume(stockPrice.getVolume());
        stockData.setTurnover(stockPrice.getTurnover());
        stockData.setTimestamp(stockPrice.getTimestamp());
        
        return stockData;
    }
}