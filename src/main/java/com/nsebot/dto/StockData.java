package com.nsebot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for Stock Market Data
 */
public class StockData {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("companyName")
    private String companyName;
    
    @JsonProperty("lastPrice")
    private BigDecimal lastPrice;
    
    @JsonProperty("change")
    private BigDecimal change;
    
    @JsonProperty("pChange")
    private BigDecimal percentChange;
    
    @JsonProperty("open")
    private BigDecimal openPrice;
    
    @JsonProperty("dayHigh")
    private BigDecimal dayHigh;
    
    @JsonProperty("dayLow")
    private BigDecimal dayLow;
    
    @JsonProperty("previousClose")
    private BigDecimal previousClose;
    
    @JsonProperty("totalTradedVolume")
    private Long volume;
    
    @JsonProperty("totalTradedValue")
    private BigDecimal turnover;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    // Constructors
    public StockData() {
        this.timestamp = LocalDateTime.now();
    }
    
    public StockData(String symbol, String companyName) {
        this();
        this.symbol = symbol;
        this.companyName = companyName;
    }
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getCompanyName() {
        return companyName;
    }
    
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }
    
    public BigDecimal getLastPrice() {
        return lastPrice;
    }
    
    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }
    
    public BigDecimal getChange() {
        return change;
    }
    
    public void setChange(BigDecimal change) {
        this.change = change;
    }
    
    public BigDecimal getPercentChange() {
        return percentChange;
    }
    
    public void setPercentChange(BigDecimal percentChange) {
        this.percentChange = percentChange;
    }
    
    public BigDecimal getOpenPrice() {
        return openPrice;
    }
    
    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }
    
    public BigDecimal getDayHigh() {
        return dayHigh;
    }
    
    public void setDayHigh(BigDecimal dayHigh) {
        this.dayHigh = dayHigh;
    }
    
    public BigDecimal getDayLow() {
        return dayLow;
    }
    
    public void setDayLow(BigDecimal dayLow) {
        this.dayLow = dayLow;
    }
    
    public BigDecimal getPreviousClose() {
        return previousClose;
    }
    
    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
    
    public BigDecimal getTurnover() {
        return turnover;
    }
    
    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("StockData{symbol='%s', lastPrice=%s, change=%s, volume=%d}", 
                           symbol, lastPrice, change, volume);
    }
}