package com.nsebot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Portfolio entity for tracking user stock holdings and performance
 */
@Entity
@Table(name = "portfolios")
public class Portfolio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;
    
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal avgBuyPrice;
    
    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;
    
    @Column(precision = 15, scale = 4)
    private BigDecimal totalInvestment;
    
    @Column(precision = 15, scale = 4)
    private BigDecimal currentValue;
    
    @Column(precision = 15, scale = 4)
    private BigDecimal unrealizedPnL;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal unrealizedPnLPercentage;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionType positionType = PositionType.LONG;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status = PositionStatus.ACTIVE;
    
    @Column
    private LocalDateTime lastPriceUpdate;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public Portfolio() {}
    
    public Portfolio(Long userId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice) {
        this.userId = userId;
        this.symbol = symbol.toUpperCase();
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
        this.totalInvestment = quantity.multiply(avgBuyPrice);
        calculateCurrentMetrics();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol.toUpperCase(); }
    
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { 
        this.quantity = quantity;
        calculateCurrentMetrics();
    }
    
    public BigDecimal getAvgBuyPrice() { return avgBuyPrice; }
    public void setAvgBuyPrice(BigDecimal avgBuyPrice) { 
        this.avgBuyPrice = avgBuyPrice;
        calculateCurrentMetrics();
    }
    
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { 
        this.currentPrice = currentPrice;
        this.lastPriceUpdate = LocalDateTime.now();
        calculateCurrentMetrics();
    }
    
    public BigDecimal getTotalInvestment() { return totalInvestment; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public BigDecimal getUnrealizedPnLPercentage() { return unrealizedPnLPercentage; }
    
    public PositionType getPositionType() { return positionType; }
    public void setPositionType(PositionType positionType) { this.positionType = positionType; }
    
    public PositionStatus getStatus() { return status; }
    public void setStatus(PositionStatus status) { this.status = status; }
    
    public LocalDateTime getLastPriceUpdate() { return lastPriceUpdate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    // Business methods
    public void addToPosition(BigDecimal additionalQuantity, BigDecimal buyPrice) {
        BigDecimal newTotalInvestment = this.totalInvestment.add(additionalQuantity.multiply(buyPrice));
        BigDecimal newTotalQuantity = this.quantity.add(additionalQuantity);
        
        this.avgBuyPrice = newTotalInvestment.divide(newTotalQuantity, 4, BigDecimal.ROUND_HALF_UP);
        this.quantity = newTotalQuantity;
        this.totalInvestment = newTotalInvestment;
        
        calculateCurrentMetrics();
    }
    
    public void reducePosition(BigDecimal reduceQuantity) {
        if (reduceQuantity.compareTo(this.quantity) >= 0) {
            // Close position completely
            this.quantity = BigDecimal.ZERO;
            this.status = PositionStatus.CLOSED;
        } else {
            this.quantity = this.quantity.subtract(reduceQuantity);
            this.totalInvestment = this.quantity.multiply(this.avgBuyPrice);
        }
        
        calculateCurrentMetrics();
    }
    
    private void calculateCurrentMetrics() {
        if (this.quantity == null || this.avgBuyPrice == null) {
            return;
        }
        
        this.totalInvestment = this.quantity.multiply(this.avgBuyPrice);
        
        if (this.currentPrice != null && this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.currentValue = this.quantity.multiply(this.currentPrice);
            this.unrealizedPnL = this.currentValue.subtract(this.totalInvestment);
            
            if (this.totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
                this.unrealizedPnLPercentage = this.unrealizedPnL
                    .divide(this.totalInvestment, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
        } else {
            this.currentValue = this.totalInvestment;
            this.unrealizedPnL = BigDecimal.ZERO;
            this.unrealizedPnLPercentage = BigDecimal.ZERO;
        }
    }
    
    public boolean isProfit() {
        return unrealizedPnL != null && unrealizedPnL.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isLoss() {
        return unrealizedPnL != null && unrealizedPnL.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public boolean needsPriceUpdate() {
        return lastPriceUpdate == null || 
               lastPriceUpdate.isBefore(LocalDateTime.now().minusMinutes(5));
    }
    
    @Override
    public String toString() {
        return String.format("Portfolio{symbol='%s', quantity=%s, avgPrice=%s, P&L=%s}", 
                           symbol, quantity, avgBuyPrice, unrealizedPnL);
    }
    
    // Enums
    public enum PositionType {
        LONG, SHORT
    }
    
    public enum PositionStatus {
        ACTIVE, CLOSED, SUSPENDED
    }
}