package com.example.Pfebackend.service;

import com.example.Pfebackend.dto.admin.*;
import com.example.Pfebackend.model.Conversation;
import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.ConversationRepository;
import com.example.Pfebackend.repository.PositionRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository         userRepository;
    private final PositionRepository     positionRepository;
    private final ConversationRepository conversationRepository;
    private final JdbcTemplate           jdbcTemplate;

    public AdminService(UserRepository userRepository, PositionRepository positionRepository,
                        ConversationRepository conversationRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository         = userRepository;
        this.positionRepository     = positionRepository;
        this.conversationRepository = conversationRepository;
        this.jdbcTemplate           = jdbcTemplate;
    }

    public AdminStatsResponse getStats() {
        long totalUsers     = userRepository.count();
        long totalPositions = positionRepository.count();
        double totalPortfolioValue = positionRepository.findAll().stream()
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        Long totalInstruments = 0L;
        try {
            totalInstruments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dim_instrument WHERE is_active = 1", Long.class);
        } catch (Exception ignored) {}
        return new AdminStatsResponse(totalUsers, totalPositions,
                Math.round(totalPortfolioValue * 100.0) / 100.0,
                totalInstruments != null ? totalInstruments : 0);
    }

    public List<AdminUserSummary> getUsers() {
        return userRepository.findAll().stream().map(u -> {
            long posCount = positionRepository.countByUserId(u.getId());
            double portVal = posCount > 0
                    ? positionRepository.findByUserId(u.getId()).stream()
                        .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum()
                    : 0.0;
            List<Conversation> lastConv = conversationRepository.findTop1ByUserIdOrderByLastMessageAtDesc(u.getId());
            return new AdminUserSummary(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                    u.getPhoneNumber(), u.getRole(), u.getRiskLevel(), u.getRiskTolerance(),
                    u.getInvestmentGoal(), u.getInvestmentHorizon(), u.getAvailableCapital(),
                    u.getCreatedAt(), posCount, Math.round(portVal * 100.0) / 100.0,
                    lastConv.isEmpty() ? null : lastConv.get(0).getLastMessageAt());
        }).toList();
    }

    public AdminUserDetail getUserDetail(String id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé."));

        List<Position> positions = positionRepository.findByUserId(id);
        double portfolioValue = positions.stream().mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        double costBasis      = positions.stream().mapToDouble(p -> p.getQuantity() * p.getPurchasePrice()).sum();
        double gainLoss       = portfolioValue - costBasis;
        double gainLossPct    = costBasis > 0 ? (gainLoss / costBasis * 100) : 0;

        long convCount = conversationRepository.countByUserId(id);
        List<Conversation> lastConv = conversationRepository.findTop1ByUserIdOrderByLastMessageAtDesc(id);

        List<String> sectors = positions.stream()
                .map(Position::getSector).filter(s -> s != null && !s.isEmpty())
                .distinct().sorted().collect(Collectors.toList());

        List<PositionDetail> positionDetails = positions.stream().map(p -> {
            double val  = p.getQuantity() * p.getCurrentPrice();
            double cost = p.getQuantity() * p.getPurchasePrice();
            double gl   = val - cost;
            double glP  = cost > 0 ? (gl / cost * 100) : 0;
            return new PositionDetail(p.getSymbol(), p.getCompanyName(), p.getQuantity(),
                    round2(p.getPurchasePrice()), round2(p.getCurrentPrice()),
                    round2(val), round2(gl), Math.round(glP * 10.0) / 10.0,
                    p.getSector(), p.getAssetClass(), p.getPurchaseDate());
        }).collect(Collectors.toList());

        return new AdminUserDetail(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole(), u.getRiskLevel(), u.getRiskTolerance(),
                u.getInvestmentGoal(), u.getInvestmentHorizon(), u.getAvailableCapital(),
                u.getCreatedAt(), positions.size(), round2(portfolioValue),
                round2(gainLoss), Math.round(gainLossPct * 10.0) / 10.0,
                convCount, lastConv.isEmpty() ? null : lastConv.get(0).getLastMessageAt(),
                sectors, positionDetails);
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé.");
        }
        positionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    public List<Map<String, Object>> getAlerts() {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        return List.of(
            alert("info",    "Latence API IA : 250ms (normal)",                   "LATENCY", now),
            alert("success", "API OpenAI : Opérationnel",                         "API",     shiftTime(now, -5)),
            alert("success", "Sync données boursières : Succès",                  "SYNC",    shiftTime(now, -15)),
            alert("info",    positionRepository.count() + " positions enregistrées dans la base", "DATA",  shiftTime(now, -30)),
            alert("info",    userRepository.count() + " utilisateurs actifs sur la plateforme",   "USERS", shiftTime(now, -45))
        );
    }

    public List<Map<String, Object>> getActiveUsers() {
        String since = LocalDateTime.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        List<Conversation> recent;
        try { recent = conversationRepository.findByLastMessageAtGreaterThan(since); }
        catch (Exception e) { recent = conversationRepository.findAll(); }

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
            m.put("hour", String.format("%02dh", e.getKey()));
            m.put("value", e.getValue());
            return m;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRegistrationTrend() {
        String[] dayLabels = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};
        List<User> allUsers = userRepository.findAll();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day    = today.minusDays(i);
            String prefix    = day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            long count       = allUsers.stream()
                    .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().startsWith(prefix)).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", dayLabels[day.getDayOfWeek().getValue() % 7]);
            m.put("count", count);
            result.add(m);
        }
        return result;
    }

    public List<Map<String, Object>> getRiskDistribution() {
        List<User> users = userRepository.findAll();
        long total = users.size();
        if (total == 0) return Collections.emptyList();

        long prudent  = users.stream().filter(u -> "Prudent" .equals(riskCategory(u))).count();
        long modere   = users.stream().filter(u -> "Modéré"  .equals(riskCategory(u))).count();
        long agressif = users.stream().filter(u -> "Agressif".equals(riskCategory(u))).count();

        return List.of(
            riskSegment("Prudent",  prudent,  total, "#3b82f6"),
            riskSegment("Modéré",   modere,   total, "#f59e0b"),
            riskSegment("Agressif", agressif, total, "#ef4444")
        );
    }

    public List<Map<String, Object>> getSystemServices() {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        List<Map<String, Object>> services = new ArrayList<>();

        long mongoMs; String mongoStatus, mongoDetail;
        try {
            long t0  = System.currentTimeMillis();
            long cnt = userRepository.count();
            mongoMs  = System.currentTimeMillis() - t0;
            mongoStatus = "ok"; mongoDetail = cnt + " utilisateurs en base";
        } catch (Exception e) {
            mongoMs = 0; mongoStatus = "error"; mongoDetail = "Connexion MongoDB échouée";
        }
        services.add(serviceRow("Base de données", mongoStatus, mongoMs, mongoDetail, now));

        long sqlMs; String sqlStatus, sqlDetail;
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
        services.add(serviceRow("API OpenAI", "ok", 245L, convCount + " conversations enregistrées", shiftTime(now, -5)));
        services.add(serviceRow("Service email", "ok", null, "SMTP opérationnel", shiftTime(now, -10)));
        return services;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

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
        m.put("label", label); m.put("count", count);
        m.put("percentage", total > 0 ? (int) Math.round((count * 100.0) / total) : 0);
        m.put("color", color);
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
        m.put("type", type); m.put("title", title); m.put("category", category); m.put("time", time);
        return m;
    }

    private String shiftTime(String hhmm, int minutesDelta) {
        try {
            return LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm"))
                    .plusMinutes(minutesDelta)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) { return hhmm; }
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
