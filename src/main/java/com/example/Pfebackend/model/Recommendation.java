package com.example.Pfebackend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "recommendations")
public class Recommendation {

    @Id
    private String id;

    private String isin;
    private String symbol;
    private String companyName;

    /** ACHAT | VENTE | CONSERVER */
    private String action;

    /** Technique | Fondamentale | Mixte */
    private String analysisType;

    private double currentPrice;
    private double targetPrice;

    /** 0-100 */
    private double confidence;

    private String rationale;

    /** Faible | Moyen | Élevé */
    private String riskLevel;

    private double rsi;
    private double macd;
    private double volatility;

    private String signalRsi;
    private String signalMacd;
    private String signalBb;

    private LocalDateTime createdAt;
    private boolean active;

    public Recommendation() {}

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(double targetPrice) { this.targetPrice = targetPrice; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public double getRsi() { return rsi; }
    public void setRsi(double rsi) { this.rsi = rsi; }

    public double getMacd() { return macd; }
    public void setMacd(double macd) { this.macd = macd; }

    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }

    public String getSignalRsi() { return signalRsi; }
    public void setSignalRsi(String signalRsi) { this.signalRsi = signalRsi; }

    public String getSignalMacd() { return signalMacd; }
    public void setSignalMacd(String signalMacd) { this.signalMacd = signalMacd; }

    public String getSignalBb() { return signalBb; }
    public void setSignalBb(String signalBb) { this.signalBb = signalBb; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
