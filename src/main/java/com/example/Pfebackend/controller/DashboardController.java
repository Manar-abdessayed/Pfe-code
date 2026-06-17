package com.example.Pfebackend.controller;

import com.example.Pfebackend.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/instruments")
    public List<Map<String, Object>> getInstruments() {
        return dashboardService.getInstruments();
    }

    @GetMapping("/ohlcv/{isin}")
    public List<Map<String, Object>> getOhlcv(
            @PathVariable String isin,
            @RequestParam(defaultValue = "30") int days) {
        return dashboardService.getOhlcv(isin, days);
    }

    @GetMapping("/technicals/{isin}")
    public Map<String, Object> getLatestTechnicals(@PathVariable String isin) {
        return dashboardService.getLatestTechnicals(isin);
    }

    @GetMapping("/top-movers")
    public Map<String, Object> getTopMovers() {
        return dashboardService.getTopMovers();
    }

    @GetMapping("/signals")
    public List<Map<String, Object>> getSignals() {
        return dashboardService.getSignals();
    }

    @GetMapping("/market-summary")
    public Map<String, Object> getMarketSummary() {
        return dashboardService.getMarketSummary();
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        return dashboardService.search(q);
    }

    @GetMapping("/signal-distribution")
    public Map<String, Object> getSignalDistribution() {
        return dashboardService.getSignalDistribution();
    }

    @GetMapping("/volume-trend")
    public List<Map<String, Object>> getVolumeTrend() {
        return dashboardService.getVolumeTrend();
    }

    @GetMapping("/rsi-zones")
    public Map<String, Object> getRsiZones() {
        return dashboardService.getRsiZones();
    }

    @GetMapping("/market-volatility")
    public List<Map<String, Object>> getMarketVolatility() {
        return dashboardService.getMarketVolatility();
    }

    @GetMapping("/macd-trend")
    public List<Map<String, Object>> getMacdTrend() {
        return dashboardService.getMacdTrend();
    }

    @GetMapping("/market-performance")
    public List<Map<String, Object>> getMarketPerformance(
            @RequestParam(defaultValue = "12") int months) {
        return dashboardService.getMarketPerformance(months);
    }
}
