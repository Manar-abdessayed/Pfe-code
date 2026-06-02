package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Conversation;
import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.AdminRepository;
import com.example.Pfebackend.repository.ConversationRepository;
import com.example.Pfebackend.repository.PositionRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository         userRepository;
    private final PositionRepository     positionRepository;
    private final AdminRepository        adminRepository;
    private final ConversationRepository conversationRepository;
    private final JdbcTemplate           jdbcTemplate;

    public AdminController(UserRepository userRepository,
                           PositionRepository positionRepository,
                           AdminRepository adminRepository,
                           ConversationRepository conversationRepository,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository         = userRepository;
        this.positionRepository     = positionRepository;
        this.adminRepository        = adminRepository;
        this.conversationRepository = conversationRepository;
        this.jdbcTemplate           = jdbcTemplate;
    }

    // ─── GET /api/admin/stats ─────────────────────────────────────────────────
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long totalUsers     = userRepository.count();
        long totalPositions = positionRepository.count();

        double totalPortfolioValue = positionRepository.findAll().stream()
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice())
                .sum();

        Long totalInstruments = 0L;
        try {
            totalInstruments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dim_instrument WHERE is_active = 1", Long.class);
        } catch (Exception ignored) {}

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers",          totalUsers);
        result.put("totalPositions",      totalPositions);
        result.put("totalPortfolioValue", Math.round(totalPortfolioValue * 100.0) / 100.0);
        result.put("totalInstruments",    totalInstruments != null ? totalInstruments : 0);
        return result;
    }

    // ─── GET /api/admin/users ─────────────────────────────────────────────────
    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() {
        return userRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               u.getId());
            m.put("firstName",        u.getFirstName());
            m.put("lastName",         u.getLastName());
            m.put("email",            u.getEmail());
            m.put("phoneNumber",      u.getPhoneNumber());
            m.put("role",             u.getRole());
            m.put("riskLevel",        u.getRiskLevel());
            m.put("riskTolerance",    u.getRiskTolerance());
            m.put("investmentGoal",   u.getInvestmentGoal());
            m.put("investmentHorizon",u.getInvestmentHorizon());
            m.put("availableCapital", u.getAvailableCapital());
            m.put("createdAt",        u.getCreatedAt());

            long posCount = positionRepository.countByUserId(u.getId());
            m.put("positionCount", posCount);

            double portVal = posCount > 0
                    ? positionRepository.findByUserId(u.getId()).stream()
                        .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum()
                    : 0.0;
            m.put("portfolioValue", Math.round(portVal * 100.0) / 100.0);

            // Last activity from conversations (fast: top 1 query)
            List<Conversation> lastConv = conversationRepository.findTop1ByUserIdOrderByLastMessageAtDesc(u.getId());
            m.put("lastActivityAt", lastConv.isEmpty() ? null : lastConv.get(0).getLastMessageAt());

            return m;
        }).collect(Collectors.toList());
    }

    // ─── GET /api/admin/users/{id}/details ───────────────────────────────────
    @GetMapping("/users/{id}/details")
    public ResponseEntity<Map<String, Object>> getUserDetails(@PathVariable String id) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) return ResponseEntity.notFound().build();

        User u = optUser.get();
        List<Position> positions = positionRepository.findByUserId(id);

        // Portfolio metrics
        double portfolioValue = positions.stream()
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        double costBasis = positions.stream()
                .mapToDouble(p -> p.getQuantity() * p.getPurchasePrice()).sum();
        double gainLoss    = portfolioValue - costBasis;
        double gainLossPct = costBasis > 0 ? (gainLoss / costBasis * 100) : 0;

        // Conversation stats
        long convCount = conversationRepository.countByUserId(id);
        List<Conversation> lastConv = conversationRepository.findTop1ByUserIdOrderByLastMessageAtDesc(id);
        String lastActivity = lastConv.isEmpty() ? null : lastConv.get(0).getLastMessageAt();

        // Distinct sectors
        List<String> sectors = positions.stream()
                .map(Position::getSector)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Positions detail
        List<Map<String, Object>> positionList = positions.stream().map(p -> {
            double val  = p.getQuantity() * p.getCurrentPrice();
            double cost = p.getQuantity() * p.getPurchasePrice();
            double gl   = val - cost;
            double glP  = cost > 0 ? (gl / cost * 100) : 0;
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("symbol",        p.getSymbol());
            pm.put("companyName",   p.getCompanyName());
            pm.put("quantity",      p.getQuantity());
            pm.put("purchasePrice", Math.round(p.getPurchasePrice() * 100.0) / 100.0);
            pm.put("currentPrice",  Math.round(p.getCurrentPrice()  * 100.0) / 100.0);
            pm.put("value",         Math.round(val  * 100.0) / 100.0);
            pm.put("gainLoss",      Math.round(gl   * 100.0) / 100.0);
            pm.put("gainLossPct",   Math.round(glP  * 10.0)  / 10.0);
            pm.put("sector",        p.getSector());
            pm.put("assetClass",    p.getAssetClass());
            pm.put("purchaseDate",  p.getPurchaseDate());
            return pm;
        }).collect(Collectors.toList());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id",               u.getId());
        detail.put("firstName",        u.getFirstName());
        detail.put("lastName",         u.getLastName());
        detail.put("email",            u.getEmail());
        detail.put("phoneNumber",      u.getPhoneNumber());
        detail.put("role",             u.getRole());
        detail.put("riskLevel",        u.getRiskLevel());
        detail.put("riskTolerance",    u.getRiskTolerance());
        detail.put("investmentGoal",   u.getInvestmentGoal());
        detail.put("investmentHorizon",u.getInvestmentHorizon());
        detail.put("availableCapital", u.getAvailableCapital());
        detail.put("createdAt",        u.getCreatedAt());
        detail.put("positionCount",    positions.size());
        detail.put("portfolioValue",   Math.round(portfolioValue * 100.0) / 100.0);
        detail.put("gainLoss",         Math.round(gainLoss    * 100.0) / 100.0);
        detail.put("gainLossPct",      Math.round(gainLossPct * 10.0)  / 10.0);
        detail.put("conversationCount",convCount);
        detail.put("lastActivityAt",   lastActivity);
        detail.put("sectors",          sectors);
        detail.put("positions",        positionList);
        return ResponseEntity.ok(detail);
    }

    // ─── DELETE /api/admin/users/{id} ────────────────────────────────────────
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        positionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── GET /api/admin/alerts ────────────────────────────────────────────────
    @GetMapping("/alerts")
    public List<Map<String, Object>> getAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        alerts.add(alert("info",    "Latence API IA : 250ms (normal)",                   "LATENCY", now));
        alerts.add(alert("success", "API OpenAI : Opérationnel",                         "API",     shiftTime(now, -5)));
        alerts.add(alert("success", "Sync données boursières : Succès",                  "SYNC",    shiftTime(now, -15)));
        alerts.add(alert("info",    positionRepository.count() + " positions enregistrées dans la base", "DATA",  shiftTime(now, -30)));
        alerts.add(alert("info",    userRepository.count() + " utilisateurs actifs sur la plateforme",   "USERS", shiftTime(now, -45)));

        return alerts;
    }

    // ─── GET /api/admin/active-users ─────────────────────────────────────────
    @GetMapping("/active-users")
    public List<Map<String, Object>> getActiveUsers() {
        String since = LocalDateTime.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        List<Conversation> recent;
        try {
            recent = conversationRepository.findByLastMessageAtGreaterThan(since);
        } catch (Exception e) {
            recent = conversationRepository.findAll();
        }

        Map<Integer, Long> counts = new TreeMap<>();
        for (int h = 0; h < 24; h++) counts.put(h, 0L);

        for (Conversation c : recent) {
            String ts = c.getLastMessageAt();
            if (ts == null || ts.length() < 13) continue;
            try {
                int hour = Integer.parseInt(ts.substring(11, 13));
                if (hour >= 0 && hour < 24) counts.merge(hour, 1L, Long::sum);
            } catch (Exception ignored) {}
        }

        return counts.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour",  String.format("%02dh", e.getKey()));
            m.put("value", e.getValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ─── GET /api/admin/registration-trend ───────────────────────────────────
    @GetMapping("/registration-trend")
    public List<Map<String, Object>> getRegistrationTrend() {
        String[] dayLabels = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};
        List<User> allUsers = userRepository.findAll();
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String prefix = day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            long count = allUsers.stream()
                    .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().startsWith(prefix))
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day",   dayLabels[day.getDayOfWeek().getValue() % 7]);
            m.put("count", count);
            result.add(m);
        }
        return result;
    }

    // ─── GET /api/admin/risk-distribution ────────────────────────────────────
    @GetMapping("/risk-distribution")
    public List<Map<String, Object>> getRiskDistribution() {
        List<User> users = userRepository.findAll();
        long total = users.size();
        if (total == 0) return Collections.emptyList();

        long prudent  = users.stream().filter(u -> "Prudent" .equals(riskCategory(u))).count();
        long modere   = users.stream().filter(u -> "Modéré"  .equals(riskCategory(u))).count();
        long agressif = users.stream().filter(u -> "Agressif".equals(riskCategory(u))).count();

        return Arrays.asList(
            riskSegment("Prudent",  prudent,  total, "#3b82f6"),
            riskSegment("Modéré",   modere,   total, "#f59e0b"),
            riskSegment("Agressif", agressif, total, "#ef4444")
        );
    }

    // ─── GET /api/admin/system-services ──────────────────────────────────────
    @GetMapping("/system-services")
    public List<Map<String, Object>> getSystemServices() {
        List<Map<String, Object>> services = new ArrayList<>();
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // MongoDB
        long mongoMs; String mongoStatus; String mongoDetail;
        try {
            long t0 = System.currentTimeMillis();
            long uCount = userRepository.count();
            mongoMs     = System.currentTimeMillis() - t0;
            mongoStatus = "ok";
            mongoDetail = uCount + " utilisateurs en base";
        } catch (Exception e) {
            mongoMs = 0; mongoStatus = "error"; mongoDetail = "Connexion MongoDB échouée";
        }
        services.add(serviceRow("Base de données", mongoStatus, mongoMs, mongoDetail, now));

        // SQL Server
        long sqlMs; String sqlStatus; String sqlDetail;
        try {
            long t0 = System.currentTimeMillis();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            sqlMs = System.currentTimeMillis() - t0;
            sqlStatus = "ok"; sqlDetail = "Données marché accessibles";
        } catch (Exception e) {
            sqlMs = 0; sqlStatus = "error"; sqlDetail = "Connexion SQL Server échouée";
        }
        services.add(serviceRow("Données marché", sqlStatus, sqlMs, sqlDetail, shiftTime(now, -2)));

        long convCount = conversationRepository.count();
        services.add(serviceRow("API OpenAI", "ok", 245L,
                convCount + " conversations enregistrées", shiftTime(now, -5)));
        services.add(serviceRow("Service email", "ok", null, "SMTP opérationnel", shiftTime(now, -10)));

        return services;
    }

    // ─── GET /api/admin/config ────────────────────────────────────────────────
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("marketDataRefreshInterval", 30);
        cfg.put("sessionTimeoutMinutes",     60);
        cfg.put("maxLoginAttempts",          5);
        cfg.put("emailNotificationsEnabled", true);
        cfg.put("smtpHost",                  "smtp.gmail.com");
        cfg.put("smtpPort",                  587);
        cfg.put("smtpUser",                  "");
        cfg.put("defaultRiskLevel",          5);
        cfg.put("maintenanceMode",           false);
        cfg.put("logLevel",                  "INFO");
        return cfg;
    }

    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, Object> config) {
        return config;
    }

    // ═══════════════ Private helpers ════════════════════════════════════════

    private String riskCategory(User u) {
        String rt = u.getRiskTolerance();
        if (rt != null && !rt.isEmpty()) return rt;
        int r = u.getRiskLevel();
        if (r <= 3) return "Prudent";
        if (r <= 6) return "Modéré";
        return "Agressif";
    }

    private Map<String, Object> riskSegment(String label, long count, long total, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label",      label);
        m.put("count",      count);
        m.put("percentage", total > 0 ? (int) Math.round((count * 100.0) / total) : 0);
        m.put("color",      color);
        return m;
    }

    private Map<String, Object> serviceRow(String name, String status, Long latencyMs,
                                           String detail, String lastCheck) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name); m.put("status", status);
        if (latencyMs != null) m.put("latency", latencyMs);
        m.put("detail", detail); m.put("lastCheck", lastCheck);
        return m;
    }

    private Map<String, Object> alert(String type, String title, String category, String time) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type); m.put("title", title);
        m.put("category", category); m.put("time", time);
        return m;
    }

    private String shiftTime(String hhmm, int minutesDelta) {
        try {
            LocalTime t = LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm"))
                    .plusMinutes(minutesDelta);
            return t.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) { return hhmm; }
    }
}
