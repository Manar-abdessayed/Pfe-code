package com.example.Pfebackend.model.bourse;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fact_technical_indic")
public class FactTechnicalIndic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "isin")
    private String isin;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "sma_20")
    private BigDecimal sma20;

    @Column(name = "sma_50")
    private BigDecimal sma50;

    @Column(name = "sma_200")
    private BigDecimal sma200;

    @Column(name = "ema_12")
    private BigDecimal ema12;

    @Column(name = "ema_26")
    private BigDecimal ema26;

    @Column(name = "rsi_14")
    private BigDecimal rsi14;

    @Column(name = "macd")
    private BigDecimal macd;

    @Column(name = "macd_signal")
    private BigDecimal macdSignal;

    @Column(name = "macd_hist")
    private BigDecimal macdHist;

    @Column(name = "bb_upper")
    private BigDecimal bbUpper;

    @Column(name = "bb_middle")
    private BigDecimal bbMiddle;

    @Column(name = "bb_lower")
    private BigDecimal bbLower;

    @Column(name = "volatility_20d")
    private BigDecimal volatility20d;

    @Column(name = "signal_rsi")
    private String signalRsi;

    @Column(name = "signal_macd")
    private String signalMacd;

    @Column(name = "signal_bb")
    private String signalBb;

    @Column(name = "daily_return_pct")
    private BigDecimal dailyReturnPct;

    // Getters
    public Long getId() { return id; }
    public String getIsin() { return isin; }
    public LocalDate getSessionDate() { return sessionDate; }
    public BigDecimal getRsi14() { return rsi14; }
    public BigDecimal getMacd() { return macd; }
    public BigDecimal getMacdSignal() { return macdSignal; }
    public BigDecimal getSma20() { return sma20; }
    public BigDecimal getSma50() { return sma50; }
    public BigDecimal getSma200() { return sma200; }
    public BigDecimal getBbUpper() { return bbUpper; }
    public BigDecimal getBbMiddle() { return bbMiddle; }
    public BigDecimal getBbLower() { return bbLower; }
    public BigDecimal getVolatility20d() { return volatility20d; }
    public String getSignalRsi() { return signalRsi; }
    public String getSignalMacd() { return signalMacd; }
    public String getSignalBb() { return signalBb; }
    public BigDecimal getDailyReturnPct() { return dailyReturnPct; }
}
