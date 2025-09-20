package com.nsebot.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for storing stock price data
 */
@Entity
@Table(name = "stock_prices", indexes = {
    @Index(name = "idx_stock_symbol", columnList = "symbol"),
    @Index(name = "idx_stock_timestamp", columnList = "timestamp"),
    @Index(name = "idx_stock_symbol_timestamp", columnList = "symbol, timestamp")
})
public class StockPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "company_name", length = 200)
    private String companyName;
    
    @Column(name = "last_price", precision = 10, scale = 2)
    private BigDecimal lastPrice;
    
    @Column(name = "change_amount", precision = 10, scale = 2)
    private BigDecimal change;
    
    @Column(name = "percent_change", precision = 6, scale = 2)
    private BigDecimal percentChange;
    
    @Column(name = "open_price", precision = 10, scale = 2)
    private BigDecimal openPrice;
    
    @Column(name = "day_high", precision = 10, scale = 2)
    private BigDecimal dayHigh;
    
    @Column(name = "day_low", precision = 10, scale = 2)
    private BigDecimal dayLow;
    
    @Column(name = "previous_close", precision = 10, scale = 2)
    private BigDecimal previousClose;
    
    @Column(name = "volume")
    private Long volume;
    
    @Column(name = "turnover", precision = 15, scale = 2)
    private BigDecimal turnover;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public StockPrice() {
        this.createdAt = LocalDateTime.now();
        this.timestamp = LocalDateTime.now();
    }
    
    public StockPrice(String symbol) {
        this();
        this.symbol = symbol;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("StockPrice{id=%d, symbol='%s', lastPrice=%s, timestamp=%s}",
                           id, symbol, lastPrice, timestamp);
    }
}