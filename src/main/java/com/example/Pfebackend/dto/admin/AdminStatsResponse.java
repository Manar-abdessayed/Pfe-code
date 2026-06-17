package com.example.Pfebackend.dto.admin;

public record AdminStatsResponse(
        long totalUsers,
        long totalPositions,
        double totalPortfolioValue,
        long totalInstruments
) {}
