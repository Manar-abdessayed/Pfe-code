package com.example.Pfebackend.service;

import com.example.Pfebackend.model.Recommendation;
import com.example.Pfebackend.repository.RecommendationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RecommendationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Value("${n8n.recommendations.url:http://localhost:5678/webhook/investia-recommendations}")
    private String n8nRecommendationsUrl;

    // ─── Public API ────────────────────────────────────────────────────────────

    public List<Recommendation> saveBatch(List<Recommendation> recs) {
        if (recs == null || recs.isEmpty()) return List.of();
        recommendationRepository.deleteByActive(true);
        LocalDateTime now = LocalDateTime.now();
        recs.forEach(r -> {
            r.setActive(true);
            r.setId(null);
            if (r.getCreatedAt() == null) r.setCreatedAt(now);
        });
        return recommendationRepository.saveAll(recs);
    }

    public List<Recommendation> getActive(String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return recommendationRepository.findByActiveOrderByConfidenceDesc(true);
        }
        String action = mapFilterToAction(filter);
        return recommendationRepository.findByActionAndActiveOrderByConfidenceDesc(action, true);
    }

    /**
     * Pulls top signals from SQL Server, enriches them (optionally via n8n),
     * replaces current active recommendations in MongoDB.
     */
    public List<Recommendation> generate() {
        // 1. Fetch top technical signals from SQL Server
        List<Map<String, Object>> rows = fetchTopSignals();

        // 2. Build Recommendation objects
        List<Recommendation> recs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Recommendation rec = buildFromRow(row);
            if (rec != null) recs.add(rec);
        }

        // 3. Optionally enrich with n8n rationale (best-effort, no failure)
        enrichWithN8n(recs);

        // 4. Replace active records in MongoDB
        recommendationRepository.deleteByActive(true);
        recs.forEach(r -> {
            r.setActive(true);
            r.setCreatedAt(LocalDateTime.now());
        });
        return recommendationRepository.saveAll(recs);
    }

    // ─── SQL ──────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> fetchTopSignals() {
        String lastDate = jdbcTemplate.queryForObject(
            "SELECT CONVERT(varchar, MAX(session_date), 23) FROM fact_technical_indicators",
            String.class
        );
        if (lastDate == null) return List.of();

        return jdbcTemplate.queryForList(
            "SELECT TOP 30 " +
            "  t.isin, f.symbol, d.short_name, d.full_name, " +
            "  t.signal_rsi, t.signal_macd, t.signal_bb, " +
            "  t.rsi_14, t.macd, t.macd_hist, " +
            "  t.bb_upper, t.bb_lower, t.bb_middle, " +
            "  t.volatility_20d, t.daily_return_pct, " +
            "  f.close_price, f.price_variation_pct " +
            "FROM fact_technical_indicators t " +
            "JOIN dim_instrument d ON t.isin = d.isin " +
            "JOIN fact_ohlcv_daily f ON t.isin = f.isin AND t.session_date = f.session_date " +
            "WHERE t.session_date = ? " +
            "  AND f.close_price IS NOT NULL AND f.close_price > 0 " +
            "  AND t.rsi_14 IS NOT NULL " +
            "ORDER BY ABS(t.rsi_14 - 50) DESC",
            lastDate
        );
    }

    // ─── Recommendation builder ───────────────────────────────────────────────

    private Recommendation buildFromRow(Map<String, Object> row) {
        try {
            String sRsi  = str(row.get("signal_rsi"));
            String sMacd = str(row.get("signal_macd"));
            String sBb   = str(row.get("signal_bb"));
            String action = computeAction(sRsi, sMacd, sBb);

            double closePrice = toDouble(row.get("close_price"));
            double rsi        = toDouble(row.get("rsi_14"));
            double macd       = toDouble(row.get("macd"));
            double macdHist   = toDouble(row.get("macd_hist"));
            double bbUpper    = toDouble(row.get("bb_upper"));
            double bbLower    = toDouble(row.get("bb_lower"));
            double volatility = toDouble(row.get("volatility_20d"));

            double targetPrice = computeTargetPrice(action, closePrice, bbUpper, bbLower);
            double confidence  = computeConfidence(action, rsi, macdHist, sRsi, sMacd, sBb);
            String riskLevel   = computeRiskLevel(volatility);

            Recommendation rec = new Recommendation();
            rec.setIsin(str(row.get("isin")));
            rec.setSymbol(str(row.get("symbol")));
            rec.setCompanyName(str(row.getOrDefault("short_name", row.get("full_name"))));
            rec.setAction(action);
            rec.setAnalysisType("Technique");
            rec.setCurrentPrice(round2(closePrice));
            rec.setTargetPrice(round2(targetPrice));
            rec.setConfidence(round2(confidence));
            rec.setRiskLevel(riskLevel);
            rec.setRsi(round2(rsi));
            rec.setMacd(round4(macd));
            rec.setVolatility(round2(volatility));
            rec.setSignalRsi(sRsi);
            rec.setSignalMacd(sMacd);
            rec.setSignalBb(sBb);
            rec.setRationale(buildDefaultRationale(action, rsi, macd, sRsi, sMacd, sBb));
            return rec;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Signal / confidence helpers ──────────────────────────────────────────

    private String computeAction(String sRsi, String sMacd, String sBb) {
        int buy = 0, sell = 0;
        if ("Buy".equalsIgnoreCase(sRsi))  buy++;  else if ("Sell".equalsIgnoreCase(sRsi))  sell++;
        if ("Buy".equalsIgnoreCase(sMacd)) buy++;  else if ("Sell".equalsIgnoreCase(sMacd)) sell++;
        if ("Buy".equalsIgnoreCase(sBb))   buy++;  else if ("Sell".equalsIgnoreCase(sBb))   sell++;
        if (buy >= 2) return "ACHAT";
        if (sell >= 2) return "VENTE";
        return "CONSERVER";
    }

    private double computeConfidence(String action, double rsi, double macdHist,
                                     String sRsi, String sMacd, String sBb) {
        double base = 55.0;

        // RSI distance from 50 → up to +25
        double rsiDist = Math.abs(rsi - 50.0);
        base += Math.min(rsiDist / 50.0 * 25.0, 25.0);

        // Each confirming signal → +5
        int confirmations = 0;
        if ("ACHAT".equals(action)) {
            if ("Buy".equalsIgnoreCase(sRsi))  confirmations++;
            if ("Buy".equalsIgnoreCase(sMacd)) confirmations++;
            if ("Buy".equalsIgnoreCase(sBb))   confirmations++;
        } else if ("VENTE".equals(action)) {
            if ("Sell".equalsIgnoreCase(sRsi))  confirmations++;
            if ("Sell".equalsIgnoreCase(sMacd)) confirmations++;
            if ("Sell".equalsIgnoreCase(sBb))   confirmations++;
        }
        base += confirmations * 5.0;

        // MACD histogram strength → up to +7
        base += Math.min(Math.abs(macdHist) * 10.0, 7.0);

        return Math.min(Math.max(base, 55.0), 97.0);
    }

    private double computeTargetPrice(String action, double close, double bbUpper, double bbLower) {
        if ("ACHAT".equals(action)) {
            return bbUpper > 0 ? bbUpper : close * 1.05;
        } else if ("VENTE".equals(action)) {
            return bbLower > 0 ? bbLower : close * 0.95;
        }
        return close * 1.02;
    }

    private String computeRiskLevel(double volatility) {
        if (volatility <= 0) return "Moyen";
        if (volatility < 1.5) return "Faible";
        if (volatility < 3.0) return "Moyen";
        return "Élevé";
    }

    private String buildDefaultRationale(String action, double rsi, double macd,
                                          String sRsi, String sMacd, String sBb) {
        StringBuilder sb = new StringBuilder();
        if ("ACHAT".equals(action)) {
            sb.append("Signal d'achat détecté. ");
            if ("Buy".equalsIgnoreCase(sRsi))  sb.append("RSI (").append(String.format("%.1f", rsi)).append(") en zone de survente. ");
            if ("Buy".equalsIgnoreCase(sMacd)) sb.append("MACD haussier. ");
            if ("Buy".equalsIgnoreCase(sBb))   sb.append("Prix proche de la bande basse de Bollinger. ");
        } else if ("VENTE".equals(action)) {
            sb.append("Signal de vente détecté. ");
            if ("Sell".equalsIgnoreCase(sRsi))  sb.append("RSI (").append(String.format("%.1f", rsi)).append(") en zone de surachat. ");
            if ("Sell".equalsIgnoreCase(sMacd)) sb.append("MACD baissier. ");
            if ("Sell".equalsIgnoreCase(sBb))   sb.append("Prix proche de la bande haute de Bollinger. ");
        } else {
            sb.append("Signaux mixtes. Conserver la position actuelle et surveiller l'évolution des indicateurs.");
        }
        return sb.toString().trim();
    }

    // ─── n8n enrichment (optional) ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void enrichWithN8n(List<Recommendation> recs) {
        if (recs.isEmpty()) return;
        try {
            RestTemplate rt = buildRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<Map<String, Object>> payload = new ArrayList<>();
            for (Recommendation r : recs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("symbol", r.getSymbol());
                item.put("company", r.getCompanyName());
                item.put("action", r.getAction());
                item.put("rsi", r.getRsi());
                item.put("macd", r.getMacd());
                item.put("confidence", r.getConfidence());
                payload.add(item);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("instruments", payload);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<List> resp = rt.exchange(n8nRecommendationsUrl, HttpMethod.POST, req, List.class);

            List<?> respList = resp.getBody();
            if (respList == null) return;

            for (int i = 0; i < Math.min(recs.size(), respList.size()); i++) {
                Object item = respList.get(i);
                if (item instanceof Map<?, ?> m) {
                    String rationale = extractText(m, "rationale", "output", "text", "message");
                    String analysisType = extractText(m, "analysisType", "type");
                    if (rationale != null && !rationale.isBlank()) {
                        recs.get(i).setRationale(rationale);
                        recs.get(i).setAnalysisType("Mixte");
                    }
                    if (analysisType != null && !analysisType.isBlank()) {
                        recs.get(i).setAnalysisType(analysisType);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[RecommendationService] n8n enrichment skipped: " + e.getMessage());
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private String mapFilterToAction(String filter) {
        return switch (filter.toLowerCase()) {
            case "achat", "buy"  -> "ACHAT";
            case "vente", "sell" -> "VENTE";
            default              -> "CONSERVER";
        };
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(30_000);
        return new RestTemplate(f);
    }

    private String extractText(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            if (m.containsKey(k) && m.get(k) != null) return m.get(k).toString().trim();
        }
        return null;
    }

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private double round2(double v)  { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v)  { return Math.round(v * 10000.0) / 10000.0; }
}
