package com.example.Pfebackend.model.bourse;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dim_instrument")
public class DimInstrument {

    @Id
    @Column(name = "isin")
    private String isin;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "market_id")
    private String marketId;

    @Column(name = "market_segment_id")
    private Integer marketSegmentId;

    @Column(name = "instrument_type")
    private String instrumentType;

    @Column(name = "currency")
    private String currency;

    @Column(name = "nominal_value")
    private BigDecimal nominalValue;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "etl_loaded_at")
    private LocalDateTime etlLoadedAt;

    // Getters & Setters
    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getMarketId() { return marketId; }
    public String getCurrency() { return currency; }
    public Boolean getIsActive() { return isActive; }
}
