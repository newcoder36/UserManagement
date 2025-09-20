package com.nsebot.service;

import com.nsebot.entity.Portfolio;
import com.nsebot.entity.User;
import com.nsebot.repository.PortfolioRepository;
import com.nsebot.service.impl.NSEDataServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing user portfolios and tracking stock positions
 */
@Service
@Transactional
public class PortfolioService {
    
    private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private NSEDataServiceImpl nseDataService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Add a new position to user's portfolio
     */
    @CacheEvict(value = "portfolios", key = "#userId")
    public Portfolio addPosition(Long userId, String symbol, BigDecimal quantity, BigDecimal buyPrice) {
        try {
            // Check if position already exists
            Optional<Portfolio> existingPosition = portfolioRepository
                .findByUserIdAndSymbolAndStatus(userId, symbol, Portfolio.PositionStatus.ACTIVE);
            
            if (existingPosition.isPresent()) {
                // Add to existing position
                Portfolio position = existingPosition.get();
                position.addToPosition(quantity, buyPrice);
                position = portfolioRepository.save(position);
                
                logger.info("Added {} shares of {} to existing position for user {}", 
                           quantity, symbol, userId);
                return position;
            } else {
                // Create new position
                Portfolio newPosition = new Portfolio(userId, symbol, quantity, buyPrice);
                
                // Set current price if available
                var stockDataOpt = nseDataService.getStockData(symbol);
                if (stockDataOpt.isPresent()) {
                    newPosition.setCurrentPrice(stockDataOpt.get().getLastPrice());
                }
                
                newPosition = portfolioRepository.save(newPosition);
                logger.info("Created new position: {} shares of {} for user {}", 
                           quantity, symbol, userId);
                return newPosition;
            }
            
        } catch (Exception e) {
            logger.error("Error adding position for user {}, symbol {}: {}", userId, symbol, e.getMessage());
            throw new RuntimeException("Failed to add position", e);
        }
    }
    
    /**
     * Update position (partial or full sale)
     */
    @CacheEvict(value = "portfolios", key = "#userId")
    public boolean updatePosition(Long userId, String symbol, BigDecimal quantityReduction) {
        try {
            Optional<Portfolio> positionOpt = portfolioRepository
                .findByUserIdAndSymbolAndStatus(userId, symbol, Portfolio.PositionStatus.ACTIVE);
            
            if (positionOpt.isPresent()) {
                Portfolio position = positionOpt.get();
                position.reducePosition(quantityReduction);
                portfolioRepository.save(position);
                
                logger.info("Reduced position by {} shares of {} for user {}", 
                           quantityReduction, symbol, userId);
                return true;
            }
            
            logger.warn("No active position found for user {}, symbol {}", userId, symbol);
            return false;
            
        } catch (Exception e) {
            logger.error("Error updating position for user {}, symbol {}: {}", userId, symbol, e.getMessage());
            return false;
        }
    }
    
    /**
     * Close position completely
     */
    @CacheEvict(value = "portfolios", key = "#userId")
    public boolean closePosition(Long userId, String symbol) {
        try {
            Optional<Portfolio> positionOpt = portfolioRepository
                .findByUserIdAndSymbolAndStatus(userId, symbol, Portfolio.PositionStatus.ACTIVE);
            
            if (positionOpt.isPresent()) {
                Portfolio position = positionOpt.get();
                position.setStatus(Portfolio.PositionStatus.CLOSED);
                portfolioRepository.save(position);
                
                logger.info("Closed position {} for user {}", symbol, userId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error closing position for user {}, symbol {}: {}", userId, symbol, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get user's active portfolio with caching
     */
    @Cacheable(value = "portfolios", key = "#userId")
    public List<Portfolio> getUserPortfolio(Long userId) {
        return portfolioRepository.findByUserIdAndStatus(userId, Portfolio.PositionStatus.ACTIVE);
    }
    
    /**
     * Get user's complete portfolio history
     */
    public List<Portfolio> getUserPortfolioHistory(Long userId) {
        return portfolioRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get portfolio summary for user
     */
    public PortfolioSummary getPortfolioSummary(Long userId) {
        try {
            Object[] summaryData = portfolioRepository.getPortfolioSummaryByUserId(userId);
            
            if (summaryData.length > 0) {
                Object[] row = summaryData;
                return new PortfolioSummary(
                    userId,
                    ((Number) row[0]).intValue(),                    // totalPositions
                    (BigDecimal) row[1],                            // totalInvestment
                    (BigDecimal) row[2],                            // currentValue
                    (BigDecimal) row[3],                            // totalUnrealizedPnL
                    (BigDecimal) row[4]                             // avgPnLPercentage
                );
            }
            
            return new PortfolioSummary(userId, 0, BigDecimal.ZERO, BigDecimal.ZERO, 
                                      BigDecimal.ZERO, BigDecimal.ZERO);
                                      
        } catch (Exception e) {
            logger.error("Error getting portfolio summary for user {}: {}", userId, e.getMessage());
            return new PortfolioSummary(userId, 0, BigDecimal.ZERO, BigDecimal.ZERO, 
                                      BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
    
    /**
     * Get profitable positions for user
     */
    public List<Portfolio> getProfitablePositions(Long userId) {
        return portfolioRepository.findProfitablePositions(userId);
    }
    
    /**
     * Get loss positions for user
     */
    public List<Portfolio> getLossPositions(Long userId) {
        return portfolioRepository.findLossPositions(userId);
    }
    
    /**
     * Get portfolio distribution (stock allocation)
     */
    public List<PortfolioDistribution> getPortfolioDistribution(Long userId) {
        try {
            List<Object[]> distributionData = portfolioRepository.getPortfolioDistribution(userId);
            
            return distributionData.stream()
                .map(row -> new PortfolioDistribution(
                    (String) row[0],        // symbol
                    (BigDecimal) row[1]     // totalValue
                ))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error getting portfolio distribution for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Update current prices for all positions (scheduled task)
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes during market hours
    public void updatePortfolioPrices() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
            List<Portfolio> positionsToUpdate = portfolioRepository.findPositionsNeedingPriceUpdate(cutoffTime);
            
            logger.info("Updating prices for {} portfolio positions", positionsToUpdate.size());
            
            // Group by symbol to minimize API calls
            var symbolGroups = positionsToUpdate.stream()
                .collect(Collectors.groupingBy(Portfolio::getSymbol));
            
            for (String symbol : symbolGroups.keySet()) {
                try {
                    var stockDataOpt = nseDataService.getStockData(symbol);
                    if (stockDataOpt.isPresent()) {
                        BigDecimal currentPrice = stockDataOpt.get().getLastPrice();
                        LocalDateTime updateTime = LocalDateTime.now();
                        
                        portfolioRepository.updateCurrentPriceForSymbol(symbol, currentPrice, updateTime);
                        logger.debug("Updated price for {}: {}", symbol, currentPrice);
                    }
                    
                    // Small delay to respect rate limits
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    logger.error("Error updating price for symbol {}: {}", symbol, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in scheduled portfolio price update: {}", e.getMessage());
        }
    }
    
    /**
     * Get top performing stocks across all portfolios
     */
    public List<StockPerformance> getTopPerformingStocks() {
        try {
            List<Object[]> performanceData = portfolioRepository.getTopPerformingStocks();
            
            return performanceData.stream()
                .limit(10) // Top 10
                .map(row -> new StockPerformance(
                    (String) row[0],                    // symbol
                    (BigDecimal) row[1],                // avgPnL
                    ((Number) row[2]).longValue()       // holders
                ))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error getting top performing stocks: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Send alerts for significant price movements
     */
    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void sendPortfolioAlerts() {
        try {
            BigDecimal alertThreshold = new BigDecimal("5.0"); // 5% movement threshold
            List<Portfolio> significantMovements = portfolioRepository
                .findPositionsWithSignificantMovement(alertThreshold);
            
            for (Portfolio position : significantMovements) {
                // Get user preferences
                Optional<User> userOpt = userService.getUserByTelegramId(position.getUserId());
                if (userOpt.isPresent() && userOpt.get().isNotificationsEnabled()) {
                    // Send notification (implement notification service)
                    logger.info("Alert: {} moved {}% for user {}", 
                               position.getSymbol(), 
                               position.getUnrealizedPnLPercentage(),
                               position.getUserId());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error sending portfolio alerts: {}", e.getMessage());
        }
    }
    
    /**
     * Portfolio summary data class
     */
    public static class PortfolioSummary {
        private final Long userId;
        private final int totalPositions;
        private final BigDecimal totalInvestment;
        private final BigDecimal currentValue;
        private final BigDecimal totalUnrealizedPnL;
        private final BigDecimal avgPnLPercentage;
        
        public PortfolioSummary(Long userId, int totalPositions, BigDecimal totalInvestment,
                               BigDecimal currentValue, BigDecimal totalUnrealizedPnL, 
                               BigDecimal avgPnLPercentage) {
            this.userId = userId;
            this.totalPositions = totalPositions;
            this.totalInvestment = totalInvestment != null ? totalInvestment : BigDecimal.ZERO;
            this.currentValue = currentValue != null ? currentValue : BigDecimal.ZERO;
            this.totalUnrealizedPnL = totalUnrealizedPnL != null ? totalUnrealizedPnL : BigDecimal.ZERO;
            this.avgPnLPercentage = avgPnLPercentage != null ? avgPnLPercentage : BigDecimal.ZERO;
        }
        
        public Long getUserId() { return userId; }
        public int getTotalPositions() { return totalPositions; }
        public BigDecimal getTotalInvestment() { return totalInvestment; }
        public BigDecimal getCurrentValue() { return currentValue; }
        public BigDecimal getTotalUnrealizedPnL() { return totalUnrealizedPnL; }
        public BigDecimal getAvgPnLPercentage() { return avgPnLPercentage; }
        
        public boolean isProfit() {
            return totalUnrealizedPnL.compareTo(BigDecimal.ZERO) > 0;
        }
        
        @Override
        public String toString() {
            return String.format("Portfolio{positions=%d, invested=%s, value=%s, pnl=%s(%.2f%%)}", 
                               totalPositions, totalInvestment, currentValue, totalUnrealizedPnL, avgPnLPercentage);
        }
    }
    
    /**
     * Portfolio distribution data class
     */
    public static class PortfolioDistribution {
        private final String symbol;
        private final BigDecimal value;
        
        public PortfolioDistribution(String symbol, BigDecimal value) {
            this.symbol = symbol;
            this.value = value;
        }
        
        public String getSymbol() { return symbol; }
        public BigDecimal getValue() { return value; }
    }
    
    /**
     * Stock performance data class
     */
    public static class StockPerformance {
        private final String symbol;
        private final BigDecimal avgPnL;
        private final long holders;
        
        public StockPerformance(String symbol, BigDecimal avgPnL, long holders) {
            this.symbol = symbol;
            this.avgPnL = avgPnL;
            this.holders = holders;
        }
        
        public String getSymbol() { return symbol; }
        public BigDecimal getAvgPnL() { return avgPnL; }
        public long getHolders() { return holders; }
    }
}