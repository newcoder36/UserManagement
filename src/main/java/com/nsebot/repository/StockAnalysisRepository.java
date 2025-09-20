package com.nsebot.repository;

import com.nsebot.entity.StockAnalysis;
import com.nsebot.entity.StockAnalysis.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for StockAnalysis entity
 */
@Repository
public interface StockAnalysisRepository extends JpaRepository<StockAnalysis, Long> {
    
    /**
     * Find the latest analysis for a given symbol
     */
    Optional<StockAnalysis> findTopBySymbolOrderByAnalyzedAtDesc(String symbol);
    
    /**
     * Find valid analyses (not expired) for a symbol
     */
    @Query("SELECT sa FROM StockAnalysis sa WHERE sa.symbol = :symbol " +
           "AND sa.validUntil > :now ORDER BY sa.analyzedAt DESC")
    List<StockAnalysis> findValidAnalyses(@Param("symbol") String symbol, 
                                         @Param("now") LocalDateTime now);
    
    /**
     * Find analyses with high confidence scores
     */
    @Query("SELECT sa FROM StockAnalysis sa WHERE sa.confidenceScore >= :minConfidence " +
           "AND sa.validUntil > :now ORDER BY sa.confidenceScore DESC")
    List<StockAnalysis> findHighConfidenceAnalyses(@Param("minConfidence") BigDecimal minConfidence,
                                                   @Param("now") LocalDateTime now);
    
    /**
     * Find analyses by recommendation type
     */
    @Query("SELECT sa FROM StockAnalysis sa WHERE sa.recommendation = :recommendation " +
           "AND sa.validUntil > :now ORDER BY sa.confidenceScore DESC")
    List<StockAnalysis> findByRecommendation(@Param("recommendation") Recommendation recommendation,
                                           @Param("now") LocalDateTime now);
    
    /**
     * Find top recommendations across all symbols
     */
    @Query("SELECT sa FROM StockAnalysis sa WHERE sa.validUntil > :now " +
           "AND sa.confidenceScore >= :minConfidence " +
           "ORDER BY sa.confidenceScore DESC")
    List<StockAnalysis> findTopRecommendations(@Param("minConfidence") BigDecimal minConfidence,
                                              @Param("now") LocalDateTime now);
    
    /**
     * Find analyses for multiple symbols
     */
    @Query("SELECT sa FROM StockAnalysis sa WHERE sa.symbol IN :symbols " +
           "AND sa.validUntil > :now " +
           "AND sa.analyzedAt = (SELECT MAX(sa2.analyzedAt) FROM StockAnalysis sa2 " +
           "WHERE sa2.symbol = sa.symbol AND sa2.validUntil > :now)")
    List<StockAnalysis> findLatestAnalysesForSymbols(@Param("symbols") List<String> symbols,
                                                    @Param("now") LocalDateTime now);
    
    /**
     * Clean up expired analyses
     */
    void deleteByValidUntilBefore(LocalDateTime cutoffDate);
    
    /**
     * Count analyses by recommendation type
     */
    @Query("SELECT sa.recommendation, COUNT(sa) FROM StockAnalysis sa " +
           "WHERE sa.validUntil > :now GROUP BY sa.recommendation")
    List<Object[]> countByRecommendationType(@Param("now") LocalDateTime now);
    
    /**
     * Find symbols with recent analyses
     */
    @Query("SELECT DISTINCT sa.symbol FROM StockAnalysis sa WHERE sa.analyzedAt >= :since")
    List<String> findSymbolsWithRecentAnalyses(@Param("since") LocalDateTime since);
    
    /**
     * Count analyses created after a specific date/time
     */
    long countByAnalyzedAtAfter(LocalDateTime cutoff);
}