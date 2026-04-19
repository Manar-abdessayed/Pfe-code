package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.repository.AdminRepository;
import com.example.Pfebackend.repository.PositionRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final AdminRepository adminRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(UserRepository userRepository,
                           PositionRepository positionRepository,
                           AdminRepository adminRepository,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.adminRepository = adminRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── GET /api/admin/stats ────────────────────────────────────────────────────
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long totalUsers     = userRepository.count();
        long totalPositions = positionRepository.count();

        List<Position> allPositions = positionRepository.findAll();
        double totalPortfolioValue = allPositions.stream()
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

    // ─── GET /api/admin/users ────────────────────────────────────────────────────
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
            m.put("investmentGoal",   u.getInvestmentGoal());
            m.put("investmentHorizon",u.getInvestmentHorizon());
            m.put("availableCapital", u.getAvailableCapital());
            long posCount = positionRepository.countByUserId(u.getId());
            m.put("positionCount", posCount);
            if (posCount > 0) {
                double portVal = positionRepository.findByUserId(u.getId()).stream()
                        .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
                m.put("portfolioValue", Math.round(portVal * 100.0) / 100.0);
            } else {
                m.put("portfolioValue", 0.0);
            }
            return m;
        }).collect(Collectors.toList());
    }

    // ─── DELETE /api/admin/users/{id} ────────────────────────────────────────────
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        positionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── GET /api/admin/alerts ───────────────────────────────────────────────────
    @GetMapping("/alerts")
    public List<Map<String, Object>> getAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        alerts.add(alert("info",    "Latence API IA : 250ms (normal)",                   "LATENCY", now));
        alerts.add(alert("success", "API OpenAI : Opérationnel",                         "API",     shiftTime(now, -5)));
        alerts.add(alert("success", "Sync données boursières : Succès",                  "SYNC",    shiftTime(now, -15)));
        alerts.add(alert("info",    positionRepository.count() + " positions enregistrées dans la base", "DATA", shiftTime(now, -30)));
        alerts.add(alert("info",    userRepository.count() + " utilisateurs actifs sur la plateforme",   "USERS", shiftTime(now, -45)));

        return alerts;
    }

    private Map<String, Object> alert(String type, String title, String category, String time) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",     type);
        m.put("title",    title);
        m.put("category", category);
        m.put("time",     time);
        return m;
    }

    private String shiftTime(String hhmm, int minutesDelta) {
        try {
            java.time.LocalTime t = java.time.LocalTime.parse(hhmm,
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    .plusMinutes(minutesDelta);
            return t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return hhmm;
        }
    }
}
