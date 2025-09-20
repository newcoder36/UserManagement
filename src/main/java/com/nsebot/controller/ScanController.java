package com.nsebot.controller;

import com.nsebot.service.SimpleScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for market scan operations
 */
@RestController
@RequestMapping("/api")
public class ScanController {

    private final SimpleScanService simpleScanService;

    @Autowired
    public ScanController(SimpleScanService simpleScanService) {
        this.simpleScanService = simpleScanService;
    }

    /**
     * Enhanced market scan with live data and strategy-based recommendations
     */
    @GetMapping("/scan")
    public ResponseEntity<String> performMarketScan() {
        String scanResult = simpleScanService.performQuickMarketScan();
        return ResponseEntity.ok(scanResult);
    }

    /**
     * Get market status information
     */
    @GetMapping("/status")
    public ResponseEntity<String> getMarketStatus() {
        String statusResult = simpleScanService.getMarketStatus();
        return ResponseEntity.ok(statusResult);
    }
}