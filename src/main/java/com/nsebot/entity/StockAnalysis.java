package com.nsebot.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for storing stock analysis results
 */
@Entity
@Table(name = "stock_analysis", indexes = {
    @Index(name = "idx_analysis_symbol", columnList = "symbol"),
    @Index(name = "idx_analysis_timestamp", columnList = "analyzed_at"),
    @Index(name = "idx_analysis_confidence", columnList = "confidence_score")
})
public class StockAnalysis {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "recommendation", length = 10)
    @Enumerated(EnumType.STRING)
    private Recommendation recommendation;
    
    @Column(name = "entry_price", precision = 10, scale = 2)
    private BigDecimal entryPrice;
    
    @Column(name = "target_price_1", precision = 10, scale = 2)
    private BigDecimal targetPrice1;
    
    @Column(name = "target_price_2", precision = 10, scale = 2)
    private BigDecimal targetPrice2;
    
    @Column(name = "stop_loss", precision = 10, scale = 2)
    private BigDecimal stopLoss;
    
    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;
    
    @Column(name = "strategies_passed")
    private Integer strategiesPassed;
    
    @Column(name = "total_strategies")
    private Integer totalStrategies;
    
    @ElementCollection
    @CollectionTable(name = "analysis_strategies", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "strategy_name")
    private List<String> passedStrategies;
    
    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(name = "time_horizon_hours")
    private Integer timeHorizonHours;
    
    @Column(name = "analysis_notes", columnDefinition = "TEXT")
    private String analysisNotes;
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    // Enums
    public enum Recommendation {
        STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, VERY_HIGH
    }
    
    // Constructors
    public StockAnalysis() {
        this.analyzedAt = LocalDateTime.now();
        this.validUntil = LocalDateTime.now().plusHours(4); // Valid for 4 hours by default
    }
    
    public StockAnalysis(String symbol) {
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
    
    public Recommendation getRecommendation() {
        return recommendation;
    }
    
    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }
    
    public BigDecimal getEntryPrice() {
        return entryPrice;
    }
    
    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }
    
    public BigDecimal getTargetPrice1() {
        return targetPrice1;
    }
    
    public void setTargetPrice1(BigDecimal targetPrice1) {
        this.targetPrice1 = targetPrice1;
    }
    
    public BigDecimal getTargetPrice2() {
        return targetPrice2;
    }
    
    public void setTargetPrice2(BigDecimal targetPrice2) {
        this.targetPrice2 = targetPrice2;
    }
    
    public BigDecimal getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public Integer getStrategiesPassed() {
        return strategiesPassed;
    }
    
    public void setStrategiesPassed(Integer strategiesPassed) {
        this.strategiesPassed = strategiesPassed;
    }
    
    public Integer getTotalStrategies() {
        return totalStrategies;
    }
    
    public void setTotalStrategies(Integer totalStrategies) {
        this.totalStrategies = totalStrategies;
    }
    
    public List<String> getPassedStrategies() {
        return passedStrategies;
    }
    
    public void setPassedStrategies(List<String> passedStrategies) {
        this.passedStrategies = passedStrategies;
    }
    
    public RiskLevel getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }
    
    public Integer getTimeHorizonHours() {
        return timeHorizonHours;
    }
    
    public void setTimeHorizonHours(Integer timeHorizonHours) {
        this.timeHorizonHours = timeHorizonHours;
    }
    
    public String getAnalysisNotes() {
        return analysisNotes;
    }
    
    public void setAnalysisNotes(String analysisNotes) {
        this.analysisNotes = analysisNotes;
    }
    
    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }
    
    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
    
    public LocalDateTime getValidUntil() {
        return validUntil;
    }
    
    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }
    
    @Override
    public String toString() {
        return String.format("StockAnalysis{id=%d, symbol='%s', recommendation=%s, confidence=%s}",
                           id, symbol, recommendation, confidenceScore);
    }
}