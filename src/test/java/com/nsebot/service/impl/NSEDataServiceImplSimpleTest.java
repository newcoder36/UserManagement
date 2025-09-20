package com.nsebot.service.impl;

import com.nsebot.client.NSEApiClient;
import com.nsebot.dto.StockData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NSEDataServiceImplSimpleTest {

    @Mock
    private NSEApiClient nseApiClient;

    @InjectMocks
    private NSEDataServiceImpl nseDataService;

    private StockData mockStockData;

    @BeforeEach
    void setUp() {
        mockStockData = new StockData("RELIANCE", "Reliance Industries Limited");
        mockStockData.setLastPrice(new BigDecimal("2500.00"));
        mockStockData.setVolume(2000000L);
        mockStockData.setTimestamp(LocalDateTime.now());
        mockStockData.setOpenPrice(new BigDecimal("2480.00"));
        mockStockData.setDayHigh(new BigDecimal("2520.00"));
        mockStockData.setDayLow(new BigDecimal("2470.00"));
        mockStockData.setPercentChange(new BigDecimal("1.2"));
    }

    @Test
    void testGetHistoricalDataBasicFunctionality() {
        // Given
        when(nseApiClient.getStockQuote(anyString())).thenReturn(Optional.of(mockStockData));
        
        // When
        List<StockData> historicalData = nseDataService.getHistoricalData("RELIANCE", 30);
        
        // Then
        assertNotNull(historicalData);
        assertEquals(30, historicalData.size());
        
        // Verify stock data is not null and has basic properties
        StockData firstPoint = historicalData.get(0);
        assertEquals("RELIANCE", firstPoint.getSymbol());
        assertNotNull(firstPoint.getLastPrice());
        assertNotNull(firstPoint.getVolume());
        assertTrue(firstPoint.getLastPrice().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(firstPoint.getVolume() > 0);
    }

    @Test
    void testGetHistoricalDataWithInvalidParameters() {
        // Test with null symbol
        List<StockData> result1 = nseDataService.getHistoricalData(null, 10);
        assertTrue(result1.isEmpty());
        
        // Test with empty symbol
        List<StockData> result2 = nseDataService.getHistoricalData("", 10);
        assertTrue(result2.isEmpty());
        
        // Test with zero days
        List<StockData> result3 = nseDataService.getHistoricalData("RELIANCE", 0);
        assertTrue(result3.isEmpty());
    }

    @Test
    void testGetHistoricalDataFallback() {
        // Given - No current data available
        when(nseApiClient.getStockQuote(anyString())).thenReturn(Optional.empty());
        
        // When
        List<StockData> historicalData = nseDataService.getHistoricalData("UNKNOWN", 10);
        
        // Then
        assertNotNull(historicalData);
        assertEquals(10, historicalData.size());
        
        // Verify fallback data generation
        StockData firstPoint = historicalData.get(0);
        assertEquals("UNKNOWN", firstPoint.getSymbol());
        assertNotNull(firstPoint.getLastPrice());
        assertTrue(firstPoint.getLastPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testGetStockDataBasicFunctionality() {
        // Given
        when(nseApiClient.getStockQuote("RELIANCE")).thenReturn(Optional.of(mockStockData));
        
        // When
        Optional<StockData> result = nseDataService.getStockData("RELIANCE");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("RELIANCE", result.get().getSymbol());
        assertEquals(new BigDecimal("2500.00"), result.get().getLastPrice());
    }
}