package com.example.Pfebackend.controller;

import com.example.Pfebackend.dto.portfolio.*;
import com.example.Pfebackend.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<Map<String, Object>>> getTradingInstruments(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(portfolioService.getTradingInstruments(q));
    }

    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<Map<String, Object>> getPositionAnalysis(@PathVariable String symbol) {
        return ResponseEntity.ok(portfolioService.getPositionAnalysis(symbol));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }

    @PostMapping("/{userId}/buy")
    public ResponseEntity<PortfolioResponse> buyStock(
            @PathVariable String userId,
            @RequestBody BuyRequest request) {
        return ResponseEntity.ok(portfolioService.buy(userId, request));
    }

    @PostMapping("/{userId}/sell")
    public ResponseEntity<PortfolioResponse> sellStock(
            @PathVariable String userId,
            @RequestBody SellRequest request) {
        return ResponseEntity.ok(portfolioService.sell(userId, request));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable String userId) {
        return ResponseEntity.ok(portfolioService.getTransactions(userId));
    }

    @PostMapping("/{userId}/positions")
    public ResponseEntity<PositionResponse> addPosition(
            @PathVariable String userId,
            @RequestBody PositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioService.addPosition(userId, request));
    }

    @PutMapping("/{userId}/positions/{id}")
    public ResponseEntity<PositionResponse> updatePosition(
            @PathVariable String userId,
            @PathVariable String id,
            @RequestBody PositionRequest request) {
        return ResponseEntity.ok(portfolioService.updatePosition(userId, id, request));
    }

    @DeleteMapping("/{userId}/positions/{id}")
    public ResponseEntity<Void> deletePosition(
            @PathVariable String userId,
            @PathVariable String id) {
        portfolioService.deletePosition(userId, id);
        return ResponseEntity.noContent().build();
    }
}
