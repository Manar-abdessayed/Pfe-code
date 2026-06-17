package com.example.Pfebackend.dto.portfolio;

public record PositionRequest(
        String symbol,
        String companyName,
        String sector,
        String assetClass,
        String purchaseDate,
        double quantity,
        double purchasePrice,
        double currentPrice
) {}
