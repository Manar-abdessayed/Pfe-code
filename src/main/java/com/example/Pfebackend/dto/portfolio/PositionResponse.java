package com.example.Pfebackend.dto.portfolio;

public record PositionResponse(
        String id,
        String userId,
        String symbol,
        String companyName,
        double quantity,
        double purchasePrice,
        double currentPrice,
        String sector,
        String assetClass,
        String purchaseDate,
        double value,
        double pl,
        double plPercent,
        double weight
) {}
