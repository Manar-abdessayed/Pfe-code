package com.example.Pfebackend.dto.admin;

import java.util.List;

public record AdminUserDetail(
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
        int positionCount,
        double portfolioValue,
        double gainLoss,
        double gainLossPct,
        long conversationCount,
        String lastActivityAt,
        List<String> sectors,
        List<PositionDetail> positions
) {}
