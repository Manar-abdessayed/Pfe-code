package com.example.Pfebackend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getInstruments() {
        return jdbcTemplate.queryForList(
            "SELECT TOP 50 isin, short_name, full_name, market_id, currency " +
            "FROM dim_instrument WHERE is_active = 1 ORDER BY short_name");
    }

    public List<Map<String, Object>> getOhlcv(String isin, int days) {
        return jdbcTemplate.queryForList(
            "SELECT TOP " + days + " isin, session_date, open_price, high_price, low_price, " +
            "close_price, volume, trade_count, price_variation_pct " +
            "FROM fact_ohlcv_daily WHERE isin = ? ORDER BY session_date DESC", isin);
    }

    public Map<String, Object> getLatestTechnicals(String isin) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "SELECT TOP 1 isin, session_date, sma_20, sma_50, sma_200, " +
            "ema_12, ema_26, rsi_14, macd, macd_signal, macd_hist, " +
            "bb_upper, bb_middle, bb_lower, volatility_20d, " +
            "signal_rsi, signal_macd, signal_bb, daily_return_pct " +
            "FROM fact_technical_indicators WHERE isin = ? ORDER BY session_date DESC", isin);
        return result.isEmpty() ? new HashMap<>() : result.get(0);
    }

    public Map<String, Object> getTopMovers() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily", String.class);
        String varExpr = "CAST((f.close_price - f.open_price) / NULLIF(f.open_price, 0) * 100 AS FLOAT)";
        String moversBase =
            "SELECT TOP 5 f.isin, f.symbol, d.short_name, f.close_price, " +
            varExpr + " AS price_variation_pct " +
            "FROM fact_ohlcv_daily f JOIN dim_instrument d ON f.isin = d.isin " +
            "WHERE f.session_date = ? AND f.open_price > 0 AND f.close_price IS NOT NULL ";
        List<Map<String, Object>> gainers = jdbcTemplate.queryForList(
            moversBase + "AND f.close_price >= f.open_price ORDER BY " + varExpr + " DESC", lastDate);
        List<Map<String, Object>> losers = jdbcTemplate.queryForList(
            moversBase + "AND f.close_price < f.open_price ORDER BY " + varExpr + " ASC", lastDate);
        Map<String, Object> result = new HashMap<>();
        result.put("date", lastDate);
        result.put("gainers", gainers);
        result.put("losers", losers);
        return result;
    }

    public List<Map<String, Object>> getSignals() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators", String.class);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT TOP 20 t.isin, f.symbol, d.short_name, " +
            "t.signal_rsi, t.signal_macd, t.signal_bb, " +
            "t.rsi_14, t.macd, f.close_price, t.daily_return_pct " +
            "FROM fact_technical_indicators t " +
            "JOIN dim_instrument d ON t.isin = d.isin " +
            "JOIN fact_ohlcv_daily f ON t.isin = f.isin AND t.session_date = f.session_date " +
            "WHERE t.session_date = ? ORDER BY ABS(t.rsi_14 - 50) DESC", lastDate);
        rows.forEach(row -> row.put("globalSignal", computeGlobalSignal(
            String.valueOf(row.get("signal_rsi")),
            String.valueOf(row.get("signal_macd")),
            String.valueOf(row.get("signal_bb")))));
        return rows;
    }

    public Map<String, Object> getMarketSummary() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily", String.class);
        Map<String, Object> stats = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) as totalInstruments, " +
            "SUM(CASE WHEN close_price > open_price THEN 1 ELSE 0 END) as hausse, " +
            "SUM(CASE WHEN close_price < open_price THEN 1 ELSE 0 END) as baisse, " +
            "SUM(CASE WHEN close_price IS NULL OR open_price IS NULL OR close_price = open_price THEN 1 ELSE 0 END) as stable, " +
            "AVG(CAST((close_price - open_price) / NULLIF(open_price, 0) * 100 AS FLOAT)) as variationMoyenne, " +
            "SUM(COALESCE(volume, 0)) as volumeTotal " +
            "FROM fact_ohlcv_daily WHERE session_date = ?", lastDate);
        stats.put("date", lastDate);
        return stats;
    }

    public List<Map<String, Object>> search(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        String pattern  = "%" + q.trim().toUpperCase() + "%";
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_ohlcv_daily", String.class);
        return jdbcTemplate.queryForList(
            "SELECT TOP 8 d.isin, d.short_name, d.full_name, f.close_price, f.price_variation_pct, f.symbol " +
            "FROM dim_instrument d " +
            "LEFT JOIN fact_ohlcv_daily f ON d.isin = f.isin AND f.session_date = ? " +
            "WHERE d.is_active = 1 AND (UPPER(d.short_name) LIKE ? OR UPPER(d.full_name) LIKE ?) " +
            "ORDER BY d.short_name", lastDate, pattern, pattern);
    }

    public Map<String, Object> getSignalDistribution() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators", String.class);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT signal_rsi, signal_macd, signal_bb FROM fact_technical_indicators WHERE session_date = ?", lastDate);
        int buy = 0, sell = 0, hold = 0;
        for (Map<String, Object> row : rows) {
            String global = computeGlobalSignal(
                String.valueOf(row.get("signal_rsi")),
                String.valueOf(row.get("signal_macd")),
                String.valueOf(row.get("signal_bb")));
            if ("Achat".equals(global)) buy++;
            else if ("Vente".equals(global)) sell++;
            else hold++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("buy", buy); result.put("sell", sell); result.put("hold", hold);
        result.put("total", buy + sell + hold);
        return result;
    }

    public List<Map<String, Object>> getVolumeTrend() {
        return jdbcTemplate.queryForList(
            "SELECT CONVERT(varchar, session_date, 23) as date, SUM(volume) as totalVolume " +
            "FROM fact_ohlcv_daily " +
            "WHERE session_date >= DATEADD(day, -30, (SELECT MAX(session_date) FROM fact_ohlcv_daily)) " +
            "GROUP BY session_date ORDER BY session_date");
    }

    public Map<String, Object> getRsiZones() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators", String.class);
        Map<String, Object> result = jdbcTemplate.queryForMap(
            "SELECT SUM(CASE WHEN rsi_14 < 30 THEN 1 ELSE 0 END) as oversold, " +
            "SUM(CASE WHEN rsi_14 >= 30 AND rsi_14 <= 70 THEN 1 ELSE 0 END) as neutral, " +
            "SUM(CASE WHEN rsi_14 > 70 THEN 1 ELSE 0 END) as overbought, COUNT(*) as total " +
            "FROM fact_technical_indicators WHERE session_date = ?", lastDate);
        result.put("date", lastDate);
        return result;
    }

    public List<Map<String, Object>> getMarketVolatility() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators", String.class);
        return jdbcTemplate.queryForList(
            "SELECT d.market_id, AVG(t.volatility_20d) as avgVolatility, COUNT(*) as cnt " +
            "FROM fact_technical_indicators t JOIN dim_instrument d ON t.isin = d.isin " +
            "WHERE t.session_date = ? AND t.volatility_20d IS NOT NULL " +
            "GROUP BY d.market_id ORDER BY avgVolatility DESC", lastDate);
    }

    public List<Map<String, Object>> getMacdTrend() {
        return jdbcTemplate.queryForList(
            "SELECT CONVERT(varchar, session_date, 23) as date, AVG(macd_hist) as avgMacd " +
            "FROM fact_technical_indicators " +
            "WHERE session_date >= DATEADD(day, -30, (SELECT MAX(session_date) FROM fact_technical_indicators)) " +
            "AND macd_hist IS NOT NULL GROUP BY session_date ORDER BY session_date");
    }

    public List<Map<String, Object>> getMarketPerformance(int months) {
        return jdbcTemplate.queryForList(
            "SELECT month, avg_variation FROM (" +
            "  SELECT TOP " + months + " CONVERT(varchar(7), session_date, 120) AS month, " +
            "  AVG(CAST(price_variation_pct AS float)) AS avg_variation " +
            "  FROM fact_ohlcv_daily WHERE price_variation_pct IS NOT NULL " +
            "  GROUP BY CONVERT(varchar(7), session_date, 120) ORDER BY month DESC" +
            ") sub ORDER BY month ASC");
    }

    private String computeGlobalSignal(String rsi, String macd, String bb) {
        int buy = 0, sell = 0;
        for (String s : new String[]{rsi, macd, bb}) {
            if ("BUY".equalsIgnoreCase(s) || "Achat".equalsIgnoreCase(s)) buy++;
            else if ("SELL".equalsIgnoreCase(s) || "Vente".equalsIgnoreCase(s)) sell++;
        }
        if (buy >= 2)  return "Achat";
        if (sell >= 2) return "Vente";
        return "Conserver";
    }
}
