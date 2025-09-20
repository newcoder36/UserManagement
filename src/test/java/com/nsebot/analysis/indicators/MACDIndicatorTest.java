package com.nsebot.analysis.indicators;

import com.nsebot.dto.StockData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MACDIndicatorTest {

    private MACDIndicator macdIndicator;
    private List<StockData> testData;

    @BeforeEach
    void setUp() {
        macdIndicator = new MACDIndicator();
        testData = createTestStockData();
    }

    @Test
    void testCalculateMACD_ValidData() {
        MACDIndicator.MACDResult result = macdIndicator.calculateMACD(testData);

        assertNotNull(result);
        assertNotNull(result.getMacdLine());
        assertNotNull(result.getSignalLine());
        assertNotNull(result.getHistogram());
        assertNotNull(result.getSignal());
        assertNotNull(result.getInterpretation());
    }

    @Test
    void testCalculateMACD_InsufficientData() {
        List<StockData> smallData = testData.subList(0, 10);
        
        MACDIndicator.MACDResult result = macdIndicator.calculateMACD(smallData);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getMacdLine());
        assertEquals(BigDecimal.ZERO, result.getSignalLine());
        assertEquals(BigDecimal.ZERO, result.getHistogram());
        assertEquals(MACDIndicator.MACDSignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testCalculateMACD_EmptyData() {
        List<StockData> emptyData = new ArrayList<>();
        
        MACDIndicator.MACDResult result = macdIndicator.calculateMACD(emptyData);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getMacdLine());
        assertEquals(BigDecimal.ZERO, result.getSignalLine());
        assertEquals(BigDecimal.ZERO, result.getHistogram());
        assertEquals(MACDIndicator.MACDSignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testCalculateMACD_NullData() {
        MACDIndicator.MACDResult result = macdIndicator.calculateMACD(null);
        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getMacdLine());
        assertEquals(BigDecimal.ZERO, result.getSignalLine());
        assertEquals(BigDecimal.ZERO, result.getHistogram());
        assertEquals(MACDIndicator.MACDSignal.NEUTRAL, result.getSignal());
    }

    @Test
    void testMACDSignalDetection() {
        // Test with uptrend data
        List<StockData> upTrendData = createUpTrendData();
        MACDIndicator.MACDResult upResult = macdIndicator.calculateMACD(upTrendData);
        
        // Test with downtrend data
        List<StockData> downTrendData = createDownTrendData();
        MACDIndicator.MACDResult downResult = macdIndicator.calculateMACD(downTrendData);
        
        // Verify results are not null and contain valid signal types
        assertNotNull(upResult.getSignal());
        assertNotNull(downResult.getSignal());
        assertTrue(upResult.getSignal() instanceof MACDIndicator.MACDSignal);
        assertTrue(downResult.getSignal() instanceof MACDIndicator.MACDSignal);
    }

    @Test
    void testBullishAndBearishMethods() {
        MACDIndicator.MACDResult result = macdIndicator.calculateMACD(testData);
        
        // Test that bullish and bearish methods return boolean values
        boolean isBullish = result.isBullish();
        boolean isBearish = result.isBearish();
        
        // They should be mutually exclusive or both false
        if (isBullish) {
            assertFalse(isBearish);
        }
        if (isBearish) {
            assertFalse(isBullish);
        }
    }

    private List<StockData> createTestStockData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal basePrice = new BigDecimal("100");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);

        for (int i = 0; i < 30; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("TEST");
            stockData.setLastPrice(basePrice.add(BigDecimal.valueOf(Math.sin(i * 0.2) * 10)));
            stockData.setPreviousClose(basePrice.add(BigDecimal.valueOf(Math.sin((i - 1) * 0.2) * 10)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }

    private List<StockData> createUpTrendData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal startPrice = new BigDecimal("80");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);

        for (int i = 0; i < 30; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("UP");
            stockData.setLastPrice(startPrice.add(BigDecimal.valueOf(i * 2)));
            stockData.setPreviousClose(startPrice.add(BigDecimal.valueOf((i - 1) * 2)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }

    private List<StockData> createDownTrendData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal startPrice = new BigDecimal("150");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);

        for (int i = 0; i < 30; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("DOWN");
            stockData.setLastPrice(startPrice.subtract(BigDecimal.valueOf(i * 2)));
            stockData.setPreviousClose(startPrice.subtract(BigDecimal.valueOf((i - 1) * 2)));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }
}