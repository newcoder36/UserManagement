package com.nsebot.controller;

import com.nsebot.service.DataSourceStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Data Source Statistics
 */
@RestController
@RequestMapping("/api/stats")
public class DataSourceStatsController {
    
    @Autowired
    private DataSourceStatsService statsService;
    
    /**
     * Get comprehensive data source statistics
     */
    @GetMapping("/sources")
    public Map<String, Object> getDataSourceStats() {
        DataSourceStatsService.DataSourceStatistics stats = statsService.getStatistics();
        
        Map<String, Object> response = new HashMap<>();
        
        // Summary counts
        response.put("summary", Map.of(
            "nse_success", stats.getNseSuccessCount(),
            "yahoo_success", stats.getYahooSuccessCount(),
            "mock_data", stats.getMockDataCount(),
            "total_success", stats.getTotalSuccessCount(),
            "total_failures", stats.getTotalFailureCount()
        ));
        
        // Success rates
        response.put("success_rates", Map.of(
            "nse_rate", String.format("%.1f%%", stats.getNseSuccessRate()),
            "yahoo_rate", String.format("%.1f%%", stats.getYahooSuccessRate())
        ));
        
        // Breakdown by symbol
        response.put("symbol_sources", stats.getSymbolToSource());
        
        // Session info
        Duration uptime = Duration.between(stats.getStartTime(), LocalDateTime.now());
        response.put("session_info", Map.of(
            "start_time", stats.getStartTime().toString(),
            "uptime_hours", uptime.toHours(),
            "uptime_minutes", uptime.toMinutes() % 60
        ));
        
        return response;
    }
    
    /**
     * Get simple summary for quick view
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        DataSourceStatsService.DataSourceStatistics stats = statsService.getStatistics();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("📊 Data Sources", "");
        summary.put("🟢 NSE API", stats.getNseSuccessCount() + " stocks");
        summary.put("🟡 Yahoo Finance", stats.getYahooSuccessCount() + " stocks"); 
        summary.put("🔴 Mock Data", stats.getMockDataCount() + " stocks");
        summary.put("❌ Total Failures", stats.getTotalFailureCount() + " attempts");
        summary.put("✅ Total Success", stats.getTotalSuccessCount() + " stocks");
        
        if (stats.getTotalSuccessCount() > 0) {
            long nsePercent = (stats.getNseSuccessCount() * 100) / stats.getTotalSuccessCount();
            long yahooPercent = (stats.getYahooSuccessCount() * 100) / stats.getTotalSuccessCount();
            long mockPercent = (stats.getMockDataCount() * 100) / stats.getTotalSuccessCount();
            
            summary.put("📈 Distribution", String.format("NSE: %d%% | Yahoo: %d%% | Mock: %d%%", 
                nsePercent, yahooPercent, mockPercent));
        }
        
        return summary;
    }
    
    /**
     * Reset statistics
     */
    @PostMapping("/reset")
    public Map<String, String> resetStats() {
        statsService.resetStatistics();
        return Map.of("message", "Statistics reset successfully", "status", "success");
    }
}