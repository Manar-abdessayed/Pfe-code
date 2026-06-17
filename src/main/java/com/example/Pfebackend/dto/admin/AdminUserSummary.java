package com.example.Pfebackend.dto.admin;

public record AdminUserSummary(
        String id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String role,
        int riskLevel,
        String riskTolerance,
        String investmentGoal,
        String investmentHorizon,
        double availableCapital,
        String createdAt,
        long positionCount,
        double portfolioValue,
        String lastActivityAt
) {}
