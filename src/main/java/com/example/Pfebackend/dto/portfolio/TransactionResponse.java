package com.example.Pfebackend.dto.portfolio;

public record TransactionResponse(
        String id,
        String type,
        String symbol,
        String companyName,
        double quantity,
        double price,
        double totalAmount,
        String transactionDate
) {}
