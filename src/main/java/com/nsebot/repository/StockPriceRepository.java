package com.nsebot.repository;

import com.nsebot.entity.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for StockPrice entity
 */
@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    
    /**
     * Find the latest stock price for a given symbol
     */
    Optional<StockPrice> findTopBySymbolOrderByTimestampDesc(String symbol);
    
    /**
     * Find stock prices for a symbol within a time range
     */
    List<StockPrice> findBySymbolAndTimestampBetweenOrderByTimestampDesc(
            String symbol, LocalDateTime start, LocalDateTime end);
    
    /**
     * Find latest prices for multiple symbols
     */
    @Query("SELECT sp FROM StockPrice sp WHERE sp.symbol IN :symbols AND " +
           "sp.timestamp = (SELECT MAX(sp2.timestamp) FROM StockPrice sp2 WHERE sp2.symbol = sp.symbol)")
    List<StockPrice> findLatestPricesForSymbols(@Param("symbols") List<String> symbols);
    
    /**
     * Find historical prices for technical analysis
     */
    @Query("SELECT sp FROM StockPrice sp WHERE sp.symbol = :symbol " +
           "AND sp.timestamp >= :fromDate ORDER BY sp.timestamp ASC")
    List<StockPrice> findHistoricalPrices(@Param("symbol") String symbol, 
                                         @Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Clean up old price data (older than specified days)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffDate);
    
    /**
     * Count records for a specific symbol
     */
    long countBySymbol(String symbol);
    
    /**
     * Find symbols with recent price updates
     */
    @Query("SELECT DISTINCT sp.symbol FROM StockPrice sp WHERE sp.timestamp >= :since")
    List<String> findSymbolsWithRecentUpdates(@Param("since") LocalDateTime since);
}