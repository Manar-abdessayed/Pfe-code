package com.example.Pfebackend.dto.profile;

import java.util.List;

public record UpdateProfileRequest(
        Integer riskLevel,
        String investmentGoal,
        String investmentHorizon,
        Double availableCapital,
        List<String> sectors
) {}
