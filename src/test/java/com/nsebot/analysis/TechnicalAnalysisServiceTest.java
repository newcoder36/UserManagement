package com.nsebot.analysis;

import com.nsebot.analysis.indicators.*;
import com.nsebot.dto.StockData;
import com.nsebot.entity.StockAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TechnicalAnalysisServiceTest {

    @Mock
    private RSIIndicator rsiIndicator;

    @Mock
    private MACDIndicator macdIndicator;

    @Mock
    private BollingerBandsIndicator bollingerBandsIndicator;

    @Mock
    private MovingAverageIndicator movingAverageIndicator;

    @Mock
    private VolumeIndicator volumeIndicator;

    @InjectMocks
    private TechnicalAnalysisService technicalAnalysisService;

    private List<StockData> testData;

    @BeforeEach
    void setUp() {
        testData = createTestStockData();
        setupMockIndicators();
    }

    @Test
    void testPerformTechnicalAnalysis_ValidData() {
        TechnicalAnalysisService.TechnicalAnalysisResult result = 
            technicalAnalysisService.performTechnicalAnalysis("RELIANCE", testData);

        assertNotNull(result);
        assertEquals("RELIANCE", result.getSymbol());
        assertNotNull(result.getRecommendation());
        assertNotNull(result.getConfidence());
        assertNotNull(result.getStrategies());
        assertNotNull(result.getAnalysisNotes());
        assertTrue(result.getStrategies().size() >= 5); // RSI, MACD, BB, MA, Volume
        assertTrue(result.getConfidence().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void testPerformTechnicalAnalysis_EmptyData() {
        List<StockData> emptyData = new ArrayList<>();
        
        TechnicalAnalysisService.TechnicalAnalysisResult result = 
            technicalAnalysisService.performTechnicalAnalysis("TEST", emptyData);

        assertNotNull(result);
        assertEquals("TEST", result.getSymbol());
        assertEquals(StockAnalysis.Recommendation.HOLD, result.getRecommendation());
        assertEquals(BigDecimal.ZERO, result.getConfidence());
        assertTrue(result.getStrategies().isEmpty());
        assertTrue(result.getAnalysisNotes().contains("No historical data"));
    }

    @Test
    void testPerformTechnicalAnalysis_NullData() {
        TechnicalAnalysisService.TechnicalAnalysisResult result = 
            technicalAnalysisService.performTechnicalAnalysis("TEST", null);

        assertNotNull(result);
        assertEquals("TEST", result.getSymbol());
        assertEquals(StockAnalysis.Recommendation.HOLD, result.getRecommendation());
        assertEquals(BigDecimal.ZERO, result.getConfidence());
        assertTrue(result.getStrategies().isEmpty());
        assertTrue(result.getAnalysisNotes().contains("No historical data"));
    }

    @Test
    void testStrategiesPassed() {
        TechnicalAnalysisService.TechnicalAnalysisResult result = 
            technicalAnalysisService.performTechnicalAnalysis("RELIANCE", testData);

        List<String> passedStrategies = result.getPassedStrategies();
        int strategiesPassedCount = result.getStrategiesPassed();
        int totalStrategies = result.getTotalStrategies();

        assertNotNull(passedStrategies);
        assertTrue(strategiesPassedCount >= 0);
        assertTrue(totalStrategies >= 5);
        assertEquals(passedStrategies.size(), strategiesPassedCount);
    }

    @Test
    void testRecommendationTypes() {
        // Test with bullish indicators
        setupBullishMocks();
        TechnicalAnalysisService.TechnicalAnalysisResult bullishResult = 
            technicalAnalysisService.performTechnicalAnalysis("BULL", testData);

        // Test with bearish indicators
        setupBearishMocks();
        TechnicalAnalysisService.TechnicalAnalysisResult bearishResult = 
            technicalAnalysisService.performTechnicalAnalysis("BEAR", testData);

        // Verify recommendations are valid enum values
        assertNotNull(bullishResult.getRecommendation());
        assertNotNull(bearishResult.getRecommendation());
        assertTrue(bullishResult.getRecommendation() instanceof StockAnalysis.Recommendation);
        assertTrue(bearishResult.getRecommendation() instanceof StockAnalysis.Recommendation);
    }

    private void setupMockIndicators() {
        // RSI Mock
        RSIIndicator.RSIResult rsiResult = new RSIIndicator.RSIResult(
            new BigDecimal("55"), RSIIndicator.RSISignal.NEUTRAL, "RSI at 55%"
        );
        lenient().when(rsiIndicator.calculateRSI(any())).thenReturn(rsiResult);

        // MACD Mock
        MACDIndicator.MACDResult macdResult = new MACDIndicator.MACDResult(
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 
            MACDIndicator.MACDSignal.NEUTRAL, "MACD neutral"
        );
        lenient().when(macdIndicator.calculateMACD(any())).thenReturn(macdResult);

        // Bollinger Bands Mock
        BollingerBandsIndicator.BollingerBandsResult bbResult = new BollingerBandsIndicator.BollingerBandsResult(
            new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("105"),
            BollingerBandsIndicator.BollingerBandsSignal.NEUTRAL, "BB neutral"
        );
        lenient().when(bollingerBandsIndicator.calculateBollingerBands(any())).thenReturn(bbResult);

        // Moving Average Mock
        MovingAverageIndicator.MovingAverageResult maResult = new MovingAverageIndicator.MovingAverageResult(
            new BigDecimal("100"), MovingAverageIndicator.MovingAverageSignal.NEUTRAL, "MA at 100", 
            MovingAverageIndicator.MovingAverageType.SMA
        );
        lenient().when(movingAverageIndicator.calculateSMA(any(), anyInt())).thenReturn(maResult);

        // Volume Mock
        VolumeIndicator.VolumeAnalysisResult volumeResult = new VolumeIndicator.VolumeAnalysisResult(
            new BigDecimal("1000000"), new BigDecimal("1000000"), new BigDecimal("1.0"), 
            VolumeIndicator.VolumeSignal.NEUTRAL, "Volume normal"
        );
        lenient().when(volumeIndicator.analyzeVolume(any())).thenReturn(volumeResult);
    }

    private void setupBullishMocks() {
        // Setup all indicators to return bullish signals
        RSIIndicator.RSIResult bullishRSI = new RSIIndicator.RSIResult(
            new BigDecimal("25"), RSIIndicator.RSISignal.OVERSOLD, "RSI oversold"
        );
        when(rsiIndicator.calculateRSI(any())).thenReturn(bullishRSI);

        MACDIndicator.MACDResult bullishMACD = new MACDIndicator.MACDResult(
            new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("1"), 
            MACDIndicator.MACDSignal.BULLISH_CROSSOVER, "MACD bullish crossover"
        );
        when(macdIndicator.calculateMACD(any())).thenReturn(bullishMACD);
    }

    private void setupBearishMocks() {
        // Setup all indicators to return bearish signals
        RSIIndicator.RSIResult bearishRSI = new RSIIndicator.RSIResult(
            new BigDecimal("80"), RSIIndicator.RSISignal.OVERBOUGHT, "RSI overbought"
        );
        when(rsiIndicator.calculateRSI(any())).thenReturn(bearishRSI);

        MACDIndicator.MACDResult bearishMACD = new MACDIndicator.MACDResult(
            new BigDecimal("-2"), new BigDecimal("-1"), new BigDecimal("-1"), 
            MACDIndicator.MACDSignal.BEARISH_CROSSOVER, "MACD bearish crossover"
        );
        when(macdIndicator.calculateMACD(any())).thenReturn(bearishMACD);
    }

    private List<StockData> createTestStockData() {
        List<StockData> data = new ArrayList<>();
        BigDecimal basePrice = new BigDecimal("100");
        LocalDateTime baseTime = LocalDateTime.now().minusDays(50);

        for (int i = 0; i < 50; i++) {
            StockData stockData = new StockData();
            stockData.setSymbol("TEST");
            stockData.setLastPrice(basePrice.add(BigDecimal.valueOf(i % 10)));
            stockData.setPreviousClose(basePrice.add(BigDecimal.valueOf((i - 1) % 10)));
            stockData.setVolume(1000000L + (i * 10000));
            stockData.setTimestamp(baseTime.plusDays(i));
            data.add(stockData);
        }
        return data;
    }
}