package com.example.Pfebackend.dto.admin;

public record PositionDetail(
        String symbol,
        String companyName,
        double quantity,
        double purchasePrice,
        double currentPrice,
        double value,
        double gainLoss,
        double gainLossPct,
        String sector,
        String assetClass,
        String purchaseDate
) {}
