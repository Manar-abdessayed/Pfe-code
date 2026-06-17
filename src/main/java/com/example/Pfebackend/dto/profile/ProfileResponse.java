package com.example.Pfebackend.dto.profile;

import java.util.List;

public record ProfileResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        int riskLevel,
        String riskTolerance,
        String investmentGoal,
        String investmentHorizon,
        double availableCapital,
        List<String> sectors
) {}
