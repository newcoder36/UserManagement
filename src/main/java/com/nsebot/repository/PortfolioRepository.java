package com.nsebot.repository;

import com.nsebot.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Portfolio entity operations
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    
    /**
     * Find all active positions for a user
     */
    List<Portfolio> findByUserIdAndStatus(Long userId, Portfolio.PositionStatus status);
    
    /**
     * Find all positions for a user (active and closed)
     */
    List<Portfolio> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Find specific position by user and symbol
     */
    Optional<Portfolio> findByUserIdAndSymbolAndStatus(Long userId, String symbol, Portfolio.PositionStatus status);
    
    /**
     * Find all positions for a specific symbol
     */
    List<Portfolio> findBySymbolAndStatus(String symbol, Portfolio.PositionStatus status);
    
    /**
     * Find positions that need price updates
     */
    @Query("SELECT p FROM Portfolio p WHERE p.status = 'ACTIVE' AND " +
           "(p.lastPriceUpdate IS NULL OR p.lastPriceUpdate < :cutoffTime)")
    List<Portfolio> findPositionsNeedingPriceUpdate(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Get user's portfolio summary
     */
    @Query("SELECT " +
           "COUNT(*) as totalPositions, " +
           "SUM(p.totalInvestment) as totalInvestment, " +
           "SUM(p.currentValue) as currentValue, " +
           "SUM(p.unrealizedPnL) as totalUnrealizedPnL, " +
           "AVG(p.unrealizedPnLPercentage) as avgPnLPercentage " +
           "FROM Portfolio p WHERE p.userId = :userId AND p.status = 'ACTIVE'")
    Object[] getPortfolioSummaryByUserId(@Param("userId") Long userId);
    
    /**
     * Find profitable positions for a user
     */
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.status = 'ACTIVE' AND p.unrealizedPnL > 0 " +
           "ORDER BY p.unrealizedPnLPercentage DESC")
    List<Portfolio> findProfitablePositions(@Param("userId") Long userId);
    
    /**
     * Find loss positions for a user
     */
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.status = 'ACTIVE' AND p.unrealizedPnL < 0 " +
           "ORDER BY p.unrealizedPnLPercentage ASC")
    List<Portfolio> findLossPositions(@Param("userId") Long userId);
    
    /**
     * Get top performing stocks across all users
     */
    @Query("SELECT p.symbol, AVG(p.unrealizedPnLPercentage) as avgPnL, COUNT(*) as holders " +
           "FROM Portfolio p WHERE p.status = 'ACTIVE' " +
           "GROUP BY p.symbol ORDER BY avgPnL DESC")
    List<Object[]> getTopPerformingStocks();
    
    /**
     * Update current price for all positions of a symbol
     */
    @Modifying
    @Query("UPDATE Portfolio p SET p.currentPrice = :currentPrice, p.lastPriceUpdate = :updateTime " +
           "WHERE p.symbol = :symbol AND p.status = 'ACTIVE'")
    int updateCurrentPriceForSymbol(@Param("symbol") String symbol, 
                                    @Param("currentPrice") BigDecimal currentPrice,
                                    @Param("updateTime") LocalDateTime updateTime);
    
    /**
     * Close position
     */
    @Modifying
    @Query("UPDATE Portfolio p SET p.status = 'CLOSED' WHERE p.id = :portfolioId")
    int closePosition(@Param("portfolioId") Long portfolioId);
    
    /**
     * Get portfolio value distribution
     */
    @Query("SELECT p.symbol, SUM(p.currentValue) as totalValue " +
           "FROM Portfolio p WHERE p.userId = :userId AND p.status = 'ACTIVE' " +
           "GROUP BY p.symbol ORDER BY totalValue DESC")
    List<Object[]> getPortfolioDistribution(@Param("userId") Long userId);
    
    /**
     * Find positions with significant price movements
     */
    @Query("SELECT p FROM Portfolio p WHERE p.status = 'ACTIVE' AND " +
           "ABS(p.unrealizedPnLPercentage) >= :thresholdPercentage")
    List<Portfolio> findPositionsWithSignificantMovement(@Param("thresholdPercentage") BigDecimal thresholdPercentage);
    
    /**
     * Get user's investment summary by date range
     */
    @Query("SELECT DATE(p.createdAt) as date, SUM(p.totalInvestment) as dailyInvestment " +
           "FROM Portfolio p WHERE p.userId = :userId AND p.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(p.createdAt) ORDER BY date")
    List<Object[]> getUserInvestmentHistory(@Param("userId") Long userId,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count active positions for user
     */
    long countByUserIdAndStatus(Long userId, Portfolio.PositionStatus status);
    
    /**
     * Find largest positions by investment amount
     */
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.status = 'ACTIVE' " +
           "ORDER BY p.totalInvestment DESC")
    List<Portfolio> findLargestPositionsByInvestment(@Param("userId") Long userId);
}