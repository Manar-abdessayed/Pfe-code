package com.example.Pfebackend.model.bourse;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fact_ohlcv_daily")
public class FactOhlcvDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "isin")
    private String isin;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "open_price")
    private BigDecimal openPrice;

    @Column(name = "high_price")
    private BigDecimal highPrice;

    @Column(name = "low_price")
    private BigDecimal lowPrice;

    @Column(name = "close_price")
    private BigDecimal closePrice;

    @Column(name = "avg_price")
    private BigDecimal avgPrice;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(name = "price_variation_pct")
    private BigDecimal priceVariationPct;

    @Column(name = "spread")
    private BigDecimal spread;

    @Column(name = "spread_pct")
    private BigDecimal spreadPct;

    // Getters
    public Long getId() { return id; }
    public String getIsin() { return isin; }
    public LocalDate getSessionDate() { return sessionDate; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public BigDecimal getVolume() { return volume; }
    public Integer getTradeCount() { return tradeCount; }
    public BigDecimal getPriceVariationPct() { return priceVariationPct; }
}
