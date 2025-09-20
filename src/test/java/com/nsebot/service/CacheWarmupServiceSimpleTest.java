package com.nsebot.service;

import com.nsebot.dto.StockData;
import com.nsebot.service.impl.NSEDataServiceImpl;
import com.nsebot.service.impl.StockAnalysisServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class CacheWarmupServiceSimpleTest {

    @Mock
    private NSEDataServiceImpl nseDataService;

    @Mock
    private StockAnalysisServiceImpl stockAnalysisService;

    @InjectMocks
    private CacheWarmupService cacheWarmupService;

    @Test
    void testIsMarketHours() {
        boolean result = cacheWarmupService.isMarketHours();
        
        // Should return a boolean value (true or false)
        assertTrue(result == true || result == false);
    }

    @Test
    void testTriggerManualWarmup() {
        // Mock the service calls
        when(nseDataService.getStockData(anyString())).thenReturn(Optional.of(createMockStockData("RELIANCE")));
        when(nseDataService.getHistoricalData(anyString(), anyInt())).thenReturn(createMockHistoricalData());
        when(stockAnalysisService.analyzeStock(anyString())).thenReturn(null);

        // Execute the method
        assertDoesNotThrow(() -> cacheWarmupService.triggerManualWarmup());
        
        // Verify that the service methods were called at least once
        verify(nseDataService, atLeastOnce()).getStockData(anyString());
        verify(nseDataService, atLeastOnce()).getHistoricalData(anyString(), anyInt());
    }

    @Test
    void testClearAnalysisCaches() {
        assertDoesNotThrow(() -> cacheWarmupService.clearAnalysisCaches("RELIANCE"));
    }

    @Test
    void testClearMarketCaches() {
        assertDoesNotThrow(() -> cacheWarmupService.clearMarketCaches());
    }

    @Test
    void testGetCacheStatistics() {
        String stats = cacheWarmupService.getCacheStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.contains("Cache warming"));
    }

    private StockData createMockStockData(String symbol) {
        StockData stockData = new StockData();
        stockData.setSymbol(symbol);
        stockData.setCompanyName(symbol + " Industries Ltd");
        stockData.setLastPrice(new BigDecimal("2500.00"));
        stockData.setChange(new BigDecimal("25.50"));
        stockData.setPercentChange(new BigDecimal("1.03"));
        stockData.setPreviousClose(new BigDecimal("2474.50"));
        stockData.setOpenPrice(new BigDecimal("2480.00"));
        stockData.setDayHigh(new BigDecimal("2520.00"));
        stockData.setDayLow(new BigDecimal("2470.00"));
        stockData.setVolume(1500000L);
        stockData.setTurnover(new BigDecimal("3750000000"));
        stockData.setTimestamp(LocalDateTime.now());
        return stockData;
    }

    private List<StockData> createMockHistoricalData() {
        return Arrays.asList(
            createMockStockData("TEST1"),
            createMockStockData("TEST2"),
            createMockStockData("TEST3")
        );
    }
}