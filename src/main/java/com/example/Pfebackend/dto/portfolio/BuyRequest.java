package com.example.Pfebackend.dto.portfolio;

public record BuyRequest(
        String symbol,
        String companyName,
        double quantity,
        double price,
        String sector,
        String assetClass
) {}
