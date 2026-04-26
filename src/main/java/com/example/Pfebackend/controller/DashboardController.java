package com.example.Pfebackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/instruments")
    public List<Map<String, Object>> getInstruments() {
        return jdbcTemplate.queryForList(
            "SELECT TOP 50 isin, short_name, full_name, market_id, currency " +
            "FROM dim_instrument WHERE is_active = 1 ORDER BY short_name"
        );
    }

    @GetMapping("/ohlcv/{isin}")
    public List<Map<String, Object>> getOhlcv(
            @PathVariable String isin,
            @RequestParam(defaultValue = "30") int days) {
        return jdbcTemplate.queryForList(
            "SELECT TOP " + days + " isin, session_date, open_price, high_price, low_price, " +
            "close_price, volume, trade_count, price_variation_pct " +
            "FROM fact_ohlcv_daily WHERE isin = ? ORDER BY session_date DESC",
            isin
        );
    }

    @GetMapping("/technicals/{isin}")
    public Map<String, Object> getLatestTechnicals(@PathVariable String isin) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "SELECT TOP 1 isin, session_date, sma_20, sma_50, sma_200, " +
            "ema_12, ema_26, rsi_14, macd, macd_signal, macd_hist, " +
            "bb_upper, bb_middle, bb_lower, volatility_20d, " +
            "signal_rsi, signal_macd, signal_bb, daily_return_pct " +
            "FROM fact_technical_indicators WHERE isin = ? ORDER BY session_date DESC",
            isin
        );
        return result.isEmpty() ? new HashMap<>() : result.get(0);
    }

    @GetMapping("/top-movers")
    public Map<String, Object> getTopMovers() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily",
            String.class
        );
        List<Map<String, Object>> gainers = jdbcTemplate.queryForList(
            "SELECT TOP 5 f.isin, f.symbol, d.short_name, f.close_price, f.price_variation_pct " +
            "FROM fact_ohlcv_daily f JOIN dim_instrument d ON f.isin = d.isin " +
            "WHERE f.session_date = ? AND f.price_variation_pct IS NOT NULL " +
            "ORDER BY f.price_variation_pct DESC", lastDate
        );
        List<Map<String, Object>> losers = jdbcTemplate.queryForList(
            "SELECT TOP 5 f.isin, f.symbol, d.short_name, f.close_price, f.price_variation_pct " +
            "FROM fact_ohlcv_daily f JOIN dim_instrument d ON f.isin = d.isin " +
            "WHERE f.session_date = ? AND f.price_variation_pct IS NOT NULL " +
            "ORDER BY f.price_variation_pct ASC", lastDate
        );
        Map<String, Object> result = new HashMap<>();
        result.put("date", lastDate);
        result.put("gainers", gainers);
        result.put("losers", losers);
        return result;
    }

    @GetMapping("/signals")
    public List<Map<String, Object>> getSignals() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators",
            String.class
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT TOP 20 t.isin, f.symbol, d.short_name, " +
            "t.signal_rsi, t.signal_macd, t.signal_bb, " +
            "t.rsi_14, t.macd, f.close_price, t.daily_return_pct " +
            "FROM fact_technical_indicators t " +
            "JOIN dim_instrument d ON t.isin = d.isin " +
            "JOIN fact_ohlcv_daily f ON t.isin = f.isin AND t.session_date = f.session_date " +
            "WHERE t.session_date = ? ORDER BY ABS(t.rsi_14 - 50) DESC", lastDate
        );
        for (Map<String, Object> row : rows) {
            row.put("globalSignal", computeGlobalSignal(
                String.valueOf(row.get("signal_rsi")),
                String.valueOf(row.get("signal_macd")),
                String.valueOf(row.get("signal_bb"))
            ));
        }
        return rows;
    }

    @GetMapping("/market-summary")
    public Map<String, Object> getMarketSummary() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily",
            String.class
        );
        Map<String, Object> stats = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) as totalInstruments, " +
            "SUM(CASE WHEN price_variation_pct > 0 THEN 1 ELSE 0 END) as hausse, " +
            "SUM(CASE WHEN price_variation_pct < 0 THEN 1 ELSE 0 END) as baisse, " +
            "SUM(CASE WHEN price_variation_pct = 0 THEN 1 ELSE 0 END) as stable, " +
            "AVG(price_variation_pct) as variationMoyenne, " +
            "SUM(volume) as volumeTotal " +
            "FROM fact_ohlcv_daily WHERE session_date = ?", lastDate
        );
        stats.put("date", lastDate);
        return stats;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        String pattern = "%" + q.trim().toUpperCase() + "%";
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily",
            String.class
        );
        return jdbcTemplate.queryForList(
            "SELECT TOP 8 d.isin, d.short_name, d.full_name, " +
            "f.close_price, f.price_variation_pct, f.symbol " +
            "FROM dim_instrument d " +
            "LEFT JOIN fact_ohlcv_daily f ON d.isin = f.isin AND f.session_date = ? " +
            "WHERE d.is_active = 1 AND (UPPER(d.short_name) LIKE ? OR UPPER(d.full_name) LIKE ?) " +
            "ORDER BY d.short_name",
            lastDate, pattern, pattern
        );
    }

    @GetMapping("/signal-distribution")
    public Map<String, Object> getSignalDistribution() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators",
            String.class
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT signal_rsi, signal_macd, signal_bb FROM fact_technical_indicators WHERE session_date = ?",
            lastDate
        );
        int buy = 0, sell = 0, hold = 0;
        for (Map<String, Object> row : rows) {
            String global = computeGlobalSignal(
                String.valueOf(row.get("signal_rsi")),
                String.valueOf(row.get("signal_macd")),
                String.valueOf(row.get("signal_bb"))
            );
            if ("Achat".equals(global)) buy++;
            else if ("Vente".equals(global)) sell++;
            else hold++;
        }
        int total = buy + sell + hold;
        Map<String, Object> result = new HashMap<>();
        result.put("buy", buy);
        result.put("sell", sell);
        result.put("hold", hold);
        result.put("total", total);
        return result;
    }

    @GetMapping("/volume-trend")
    public List<Map<String, Object>> getVolumeTrend() {
        return jdbcTemplate.queryForList(
            "SELECT CONVERT(varchar, session_date, 23) as date, SUM(volume) as totalVolume " +
            "FROM fact_ohlcv_daily " +
            "WHERE session_date >= DATEADD(day, -30, GETDATE()) " +
            "GROUP BY session_date ORDER BY session_date"
        );
    }

    @GetMapping("/rsi-zones")
    public Map<String, Object> getRsiZones() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators",
            String.class
        );
        Map<String, Object> result = jdbcTemplate.queryForMap(
            "SELECT " +
            "SUM(CASE WHEN rsi_14 < 30 THEN 1 ELSE 0 END) as oversold, " +
            "SUM(CASE WHEN rsi_14 >= 30 AND rsi_14 <= 70 THEN 1 ELSE 0 END) as neutral, " +
            "SUM(CASE WHEN rsi_14 > 70 THEN 1 ELSE 0 END) as overbought, " +
            "COUNT(*) as total " +
            "FROM fact_technical_indicators WHERE session_date = ?",
            lastDate
        );
        result.put("date", lastDate);
        return result;
    }

    @GetMapping("/market-volatility")
    public List<Map<String, Object>> getMarketVolatility() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators",
            String.class
        );
        return jdbcTemplate.queryForList(
            "SELECT d.market_id, AVG(t.volatility_20d) as avgVolatility, COUNT(*) as cnt " +
            "FROM fact_technical_indicators t " +
            "JOIN dim_instrument d ON t.isin = d.isin " +
            "WHERE t.session_date = ? AND t.volatility_20d IS NOT NULL " +
            "GROUP BY d.market_id ORDER BY avgVolatility DESC",
            lastDate
        );
    }

    @GetMapping("/macd-trend")
    public List<Map<String, Object>> getMacdTrend() {
        return jdbcTemplate.queryForList(
            "SELECT CONVERT(varchar, session_date, 23) as date, AVG(macd_hist) as avgMacd " +
            "FROM fact_technical_indicators " +
            "WHERE session_date >= DATEADD(day, -30, GETDATE()) AND macd_hist IS NOT NULL " +
            "GROUP BY session_date ORDER BY session_date"
        );
    }

    // ─── GET /api/dashboard/market-performance ───────────────────────────────
    @GetMapping("/market-performance")
    public List<Map<String, Object>> getMarketPerformance(
            @RequestParam(defaultValue = "12") int months) {

        return jdbcTemplate.queryForList(
            "SELECT month, avg_variation FROM (" +
            "  SELECT TOP " + months + " " +
            "  CONVERT(varchar(7), session_date, 120) AS month, " +
            "  AVG(CAST(price_variation_pct AS float)) AS avg_variation " +
            "  FROM fact_ohlcv_daily " +
            "  WHERE price_variation_pct IS NOT NULL " +
            "  GROUP BY CONVERT(varchar(7), session_date, 120) " +
            "  ORDER BY month DESC" +
            ") sub ORDER BY month ASC"
        );
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