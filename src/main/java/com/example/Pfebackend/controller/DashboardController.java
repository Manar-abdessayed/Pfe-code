package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.bourse.DimInstrument;
import com.example.Pfebackend.model.bourse.FactOhlcvDaily;
import com.example.Pfebackend.model.bourse.FactTechnicalIndic;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────
    // GET /api/dashboard/instruments
    // Liste tous les instruments actifs
    // ─────────────────────────────────────────────
    @GetMapping("/instruments")
    public List<DimInstrument> getInstruments() {
        return em.createQuery(
            "SELECT d FROM DimInstrument d WHERE d.isActive = true ORDER BY d.symbol",
            DimInstrument.class
        ).setMaxResults(50).getResultList();
    }

    // ─────────────────────────────────────────────
    // GET /api/dashboard/ohlcv/{isin}?days=30
    // Prix OHLCV des N derniers jours pour un instrument
    // ─────────────────────────────────────────────
    @GetMapping("/ohlcv/{isin}")
    public List<FactOhlcvDaily> getOhlcv(
            @PathVariable String isin,
            @RequestParam(defaultValue = "30") int days) {
        return em.createQuery(
            "SELECT f FROM FactOhlcvDaily f WHERE f.isin = :isin " +
            "ORDER BY f.sessionDate DESC",
            FactOhlcvDaily.class
        ).setParameter("isin", isin)
         .setMaxResults(days)
         .getResultList();
    }

    // ─────────────────────────────────────────────
    // GET /api/dashboard/technicals/{isin}
    // Derniers indicateurs techniques pour un instrument
    // ─────────────────────────────────────────────
    @GetMapping("/technicals/{isin}")
    public FactTechnicalIndic getLatestTechnicals(@PathVariable String isin) {
        List<FactTechnicalIndic> result = em.createQuery(
            "SELECT t FROM FactTechnicalIndic t WHERE t.isin = :isin " +
            "ORDER BY t.sessionDate DESC",
            FactTechnicalIndic.class
        ).setParameter("isin", isin)
         .setMaxResults(1)
         .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }

    // ─────────────────────────────────────────────
    // GET /api/dashboard/top-movers
    // Top 5 hausses et baisses du jour
    // ─────────────────────────────────────────────
    @GetMapping("/top-movers")
    public Map<String, Object> getTopMovers() {
        // Dernière date disponible
        Object lastDate = em.createQuery(
            "SELECT MAX(f.sessionDate) FROM FactOhlcvDaily f"
        ).getSingleResult();

        List<Object[]> gainers = em.createNativeQuery(
            "SELECT TOP 5 f.isin, d.symbol, d.short_name, f.close_price, f.price_variation_pct " +
            "FROM fact_ohlcv_daily f " +
            "JOIN dim_instrument d ON f.isin = d.isin " +
            "WHERE f.session_date = :date AND f.price_variation_pct IS NOT NULL " +
            "ORDER BY f.price_variation_pct DESC"
        ).setParameter("date", lastDate).getResultList();

        List<Object[]> losers = em.createNativeQuery(
            "SELECT TOP 5 f.isin, d.symbol, d.short_name, f.close_price, f.price_variation_pct " +
            "FROM fact_ohlcv_daily f " +
            "JOIN dim_instrument d ON f.isin = d.isin " +
            "WHERE f.session_date = :date AND f.price_variation_pct IS NOT NULL " +
            "ORDER BY f.price_variation_pct ASC"
        ).setParameter("date", lastDate).getResultList();

        Map<String, Object> result = new HashMap<>();
        result.put("date", lastDate);
        result.put("gainers", formatMovers(gainers));
        result.put("losers", formatMovers(losers));
        return result;
    }

    // ─────────────────────────────────────────────
    // GET /api/dashboard/signals
    // Signaux IA du dernier jour (Achat/Vente/Conserver)
    // ─────────────────────────────────────────────
    @GetMapping("/signals")
    public List<Map<String, Object>> getSignals() {
        Object lastDate = em.createQuery(
            "SELECT MAX(t.sessionDate) FROM FactTechnicalIndic t"
        ).getSingleResult();

        List<Object[]> rows = em.createNativeQuery(
            "SELECT TOP 20 t.isin, d.symbol, d.short_name, " +
            "t.signal_rsi, t.signal_macd, t.signal_bb, " +
            "t.rsi_14, t.macd, t.close_price, t.daily_return_pct " +
            "FROM fact_technical_indic t " +
            "JOIN dim_instrument d ON t.isin = d.isin " +
            "JOIN fact_ohlcv_daily f ON t.isin = f.isin AND t.session_date = f.session_date " +
            "WHERE t.session_date = :date " +
            "ORDER BY ABS(t.rsi_14 - 50) DESC"
        ).setParameter("date", lastDate).getResultList();

        List<Map<String, Object>> signals = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("isin", row[0]);
            s.put("symbol", row[1]);
            s.put("shortName", row[2]);
            s.put("signalRsi", row[3]);
            s.put("signalMacd", row[4]);
            s.put("signalBb", row[5]);
            s.put("rsi14", row[6]);
            s.put("macd", row[7]);
            s.put("closePrice", row[8]);
            s.put("dailyReturnPct", row[9]);
            // Signal global basé sur majorité
            s.put("globalSignal", computeGlobalSignal(
                String.valueOf(row[3]),
                String.valueOf(row[4]),
                String.valueOf(row[5])
            ));
            signals.add(s);
        }
        return signals;
    }

    // ─────────────────────────────────────────────
    // GET /api/dashboard/market-summary
    // Résumé général du marché
    // ─────────────────────────────────────────────
    @GetMapping("/market-summary")
    public Map<String, Object> getMarketSummary() {
        Object lastDate = em.createQuery(
            "SELECT MAX(f.sessionDate) FROM FactOhlcvDaily f"
        ).getSingleResult();

        Object[] stats = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN price_variation_pct > 0 THEN 1 ELSE 0 END) as hausse, " +
            "SUM(CASE WHEN price_variation_pct < 0 THEN 1 ELSE 0 END) as baisse, " +
            "SUM(CASE WHEN price_variation_pct = 0 THEN 1 ELSE 0 END) as stable, " +
            "AVG(price_variation_pct) as variation_moy, " +
            "SUM(volume) as volume_total " +
            "FROM fact_ohlcv_daily WHERE session_date = :date"
        ).setParameter("date", lastDate).getSingleResult();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("date", lastDate);
        summary.put("totalInstruments", stats[0]);
        summary.put("hausse", stats[1]);
        summary.put("baisse", stats[2]);
        summary.put("stable", stats[3]);
        summary.put("variationMoyenne", stats[4]);
        summary.put("volumeTotal", stats[5]);
        return summary;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────
    private List<Map<String, Object>> formatMovers(List<Object[]> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("isin", row[0]);
            m.put("symbol", row[1]);
            m.put("shortName", row[2]);
            m.put("closePrice", row[3]);
            m.put("variationPct", row[4]);
            list.add(m);
        }
        return list;
    }

    private String computeGlobalSignal(String rsi, String macd, String bb) {
        int buy = 0, sell = 0;
        if ("BUY".equalsIgnoreCase(rsi) || "Achat".equalsIgnoreCase(rsi)) buy++;
        else if ("SELL".equalsIgnoreCase(rsi) || "Vente".equalsIgnoreCase(rsi)) sell++;
        if ("BUY".equalsIgnoreCase(macd) || "Achat".equalsIgnoreCase(macd)) buy++;
        else if ("SELL".equalsIgnoreCase(macd) || "Vente".equalsIgnoreCase(macd)) sell++;
        if ("BUY".equalsIgnoreCase(bb) || "Achat".equalsIgnoreCase(bb)) buy++;
        else if ("SELL".equalsIgnoreCase(bb) || "Vente".equalsIgnoreCase(bb)) sell++;
        if (buy >= 2) return "Achat";
        if (sell >= 2) return "Vente";
        return "Conserver";
    }
}
