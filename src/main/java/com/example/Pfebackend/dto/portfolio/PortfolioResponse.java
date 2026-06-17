package com.example.Pfebackend.dto.portfolio;

import java.util.List;

public record PortfolioResponse(
        List<PositionResponse> positions,
        double totalValue,
        double totalCost,
        double totalPL,
        double totalPLPercent,
        double availableCapital,
        List<BreakdownItem> sectorBreakdown,
        List<BreakdownItem> assetClassBreakdown,
        List<EvolutionPoint> evolutionData
) {}
