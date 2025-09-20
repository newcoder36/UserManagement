package com.nsebot.controller;

import com.nsebot.dto.StockData;
import com.nsebot.service.NSEDataService;
import com.nsebot.service.DataSourceStatsService;
import com.nsebot.service.SimpleScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for stock data operations - Testing enhanced live data fetching
 */
@RestController
@RequestMapping("/api/stock")
public class StockDataController {

    private final NSEDataService nseDataService;
    private final DataSourceStatsService statsService;
    private final SimpleScanService simpleScanService;

    @Autowired
    public StockDataController(NSEDataService nseDataService, DataSourceStatsService statsService, SimpleScanService simpleScanService) {
        this.nseDataService = nseDataService;
        this.statsService = statsService;
        this.simpleScanService = simpleScanService;
    }

    /**
     * Get stock data for a single symbol - Tests smart anti-bot features
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<StockData> getStock(@PathVariable String symbol) {
        Optional<StockData> stockData = nseDataService.getStockData(symbol.toUpperCase());
        
        return stockData
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get stock data for multiple symbols - Tests rate limiting and throttling
     */
    @GetMapping("/batch/{symbols}")
    public ResponseEntity<List<StockData>> getMultipleStocks(@PathVariable String symbols) {
        List<String> symbolList = List.of(symbols.toUpperCase().split(","));
        List<StockData> stockDataList = nseDataService.getMultipleStockData(symbolList);
        
        return ResponseEntity.ok(stockDataList);
    }

    /**
     * Test endpoint for NSE anti-bot evasion
     */
    @GetMapping("/test/nse/{symbol}")
    public ResponseEntity<Object> testNSEAntiBot(@PathVariable String symbol) {
        Optional<StockData> stockData = nseDataService.getStockData(symbol.toUpperCase());
        
        if (stockData.isPresent()) {
            return ResponseEntity.ok(stockData.get());
        } else {
            return ResponseEntity.ok("⚠️ NSE API test failed - check logs for details");
        }
    }

    /**
     * Get current statistics about data source usage
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> getDataSourceStats() {
        return ResponseEntity.ok(statsService.getStatistics());
    }

    /**
     * Enhanced market scan with live data and strategy-based recommendations
     */
    @GetMapping("/scan")
    public ResponseEntity<String> performMarketScan() {
        String scanResult = simpleScanService.performQuickMarketScan();
        return ResponseEntity.ok(scanResult);
    }
}