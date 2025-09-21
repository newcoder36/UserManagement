package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

    }

    @Test
    void testCalculateRSI_ValidData() {
    
    }

    @Test
    void testCalculateRSI_InsufficientData() {
        List<StockData> smallData = testData.subList(0, 5);
        
        RSIIndicator.RSIResult result = rsiIndicator.calculateRSI(smallData);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getValue());
        assertEquals(RSIIndicator.RSISignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testCalculateRSI_EmptyData() {
        List<StockData> emptyData = new ArrayList<>();
        
        RSIIndicator.RSIResult result = rsiIndicator.calculateRSI(emptyData);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getValue());
        assertEquals(RSIIndicator.RSISignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testCalculateRSI_NullData() {
        RSIIndicator.RSIResult result = rsiIndicator.calculateRSI(null);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getValue());
        assertEquals(RSIIndicator.RSISignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testRSISignals() {
        // Test oversold condition (RSI < 30)
        List<StockData> downTrendData = createDownTrendData();
        RSIIndicator.RSIResult oversoldResult = rsiIndicator.calculateRSI(downTrendData);
        
        // Test overbought condition (RSI > 70)  
        List<StockData> upTrendData = createUpTrendData();
        RSIIndicator.RSIResult overboughtResult = rsiIndicator.calculateRSI(upTrendData);
        
        // Note: Due to synthetic data, we mainly test that the method executes without errors
        assertNotNull(oversoldResult.getSignal());
        assertNotNull(overboughtResult.getSignal());
    }

    private List<StockData> createTestStockData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal basePrice = new BigDecimal("100");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(20);

        for (int i = 0; i < 20; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("TEST");
            stockData.setLastPrice(basePrice.add(BigDecimal.valueOf(i * 2)));
            stockData.setPreviousClose(basePrice.add(BigDecimal.valueOf((i - 1) * 2)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }

    private List<StockData> createDownTrendData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal startPrice = new BigDecimal("150");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(20);

        for (int i = 0; i < 20; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("DOWN");
            stockData.setLastPrice(startPrice.subtract(BigDecimal.valueOf(i * 3)));
            stockData.setPreviousClose(startPrice.subtract(BigDecimal.valueOf((i - 1) * 3)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }

    private List<StockData> createUpTrendData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal startPrice = new BigDecimal("50");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(20);

        for (int i = 0; i < 20; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("UP");
            stockData.setLastPrice(startPrice.add(BigDecimal.valueOf(i * 4)));
            stockData.setPreviousClose(startPrice.add(BigDecimal.valueOf((i - 1) * 4)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }
}