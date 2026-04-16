package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.repository.PositionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PositionRepository positionRepository;

    public PortfolioController(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    // ─── GET /api/portfolio/{userId} ────────────────────────────────────────────
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable String userId) {
        List<Position> positions = positionRepository.findByUserId(userId);

        double totalValue = 0;
        double totalCost = 0;
        for (Position p : positions) {
            totalValue += p.getQuantity() * p.getCurrentPrice();
            totalCost  += p.getQuantity() * p.getPurchasePrice();
        }
        final double tv = totalValue;
        final double tc = totalCost;
        double totalPL = tv - tc;
        double totalPLPercent = tc > 0 ? (totalPL / tc) * 100 : 0;

        List<Map<String, Object>> positionItems = positions.stream()
                .map(p -> toPositionItem(p, tv))
                .collect(Collectors.toList());

        List<Map<String, Object>> sectorBreakdown     = buildBreakdown(positions, Position::getSector,     tv);
        List<Map<String, Object>> assetClassBreakdown = buildBreakdown(positions, Position::getAssetClass, tv);
        List<Map<String, Object>> evolutionData = buildEvolution(positions);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("positions", positionItems);
        response.put("totalValue", round2(totalValue));
        response.put("totalCost", round2(totalCost));
        response.put("totalPL", round2(totalPL));
        response.put("totalPLPercent", round2(totalPLPercent));
        response.put("sectorBreakdown", sectorBreakdown);
        response.put("assetClassBreakdown", assetClassBreakdown);
        response.put("evolutionData", evolutionData);

        return ResponseEntity.ok(response);
    }

    // ─── POST /api/portfolio/{userId}/positions ──────────────────────────────────
    @PostMapping("/{userId}/positions")
    public ResponseEntity<Map<String, Object>> addPosition(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        Position pos = fromRequest(request, userId);
        Position saved = positionRepository.save(pos);

        List<Position> all = positionRepository.findByUserId(userId);
        double totalValue = all.stream().mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();

        return ResponseEntity.status(HttpStatus.CREATED).body(toPositionItem(saved, totalValue));
    }

    // ─── PUT /api/portfolio/{userId}/positions/{id} ──────────────────────────────
    @PutMapping("/{userId}/positions/{id}")
    public ResponseEntity<Map<String, Object>> updatePosition(
            @PathVariable String userId,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {

        Optional<Position> opt = positionRepository.findByIdAndUserId(id, userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Position non trouvée."));
        }

        Position pos = opt.get();
        applyRequest(pos, request);
        Position saved = positionRepository.save(pos);

        List<Position> all = positionRepository.findByUserId(userId);
        double totalValue = all.stream().mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();

        return ResponseEntity.ok(toPositionItem(saved, totalValue));
    }

    // ─── DELETE /api/portfolio/{userId}/positions/{id} ───────────────────────────
    @DeleteMapping("/{userId}/positions/{id}")
    public ResponseEntity<Void> deletePosition(
            @PathVariable String userId,
            @PathVariable String id) {

        Optional<Position> opt = positionRepository.findByIdAndUserId(id, userId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        positionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Position fromRequest(Map<String, Object> req, String userId) {
        Position pos = new Position();
        pos.setUserId(userId);
        applyRequest(pos, req);
        return pos;
    }

    private void applyRequest(Position pos, Map<String, Object> req) {
        if (req.containsKey("symbol"))       pos.setSymbol(req.get("symbol").toString());
        if (req.containsKey("companyName"))  pos.setCompanyName(req.get("companyName").toString());
        if (req.containsKey("sector"))       pos.setSector(req.get("sector").toString());
        if (req.containsKey("assetClass"))   pos.setAssetClass(req.get("assetClass").toString());
        if (req.containsKey("purchaseDate")) pos.setPurchaseDate(req.get("purchaseDate").toString());
        if (req.containsKey("quantity"))     pos.setQuantity(Double.parseDouble(req.get("quantity").toString()));
        if (req.containsKey("purchasePrice")) pos.setPurchasePrice(Double.parseDouble(req.get("purchasePrice").toString()));
        if (req.containsKey("currentPrice")) pos.setCurrentPrice(Double.parseDouble(req.get("currentPrice").toString()));
    }

    private Map<String, Object> toPositionItem(Position pos, double totalValue) {
        double value      = pos.getQuantity() * pos.getCurrentPrice();
        double cost       = pos.getQuantity() * pos.getPurchasePrice();
        double pl         = value - cost;
        double plPercent  = cost > 0 ? (pl / cost) * 100 : 0;
        double weight     = totalValue > 0 ? (value / totalValue) * 100 : 0;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id",            pos.getId());
        item.put("userId",        pos.getUserId());
        item.put("symbol",        pos.getSymbol());
        item.put("companyName",   pos.getCompanyName());
        item.put("quantity",      pos.getQuantity());
        item.put("purchasePrice", pos.getPurchasePrice());
        item.put("currentPrice",  pos.getCurrentPrice());
        item.put("sector",        pos.getSector());
        item.put("assetClass",    pos.getAssetClass());
        item.put("purchaseDate",  pos.getPurchaseDate());
        item.put("value",         round2(value));
        item.put("pl",            round2(pl));
        item.put("plPercent",     round2(plPercent));
        item.put("weight",        round1(weight));
        return item;
    }

    private List<Map<String, Object>> buildBreakdown(
            List<Position> positions,
            Function<Position, String> fieldGetter,
            double totalValue) {

        if (totalValue == 0) return List.of();

        Map<String, Double> valueMap = new LinkedHashMap<>();
        for (Position p : positions) {
            String key = fieldGetter.apply(p);
            if (key == null) key = "Autres";
            valueMap.merge(key, p.getQuantity() * p.getCurrentPrice(), Double::sum);
        }

        return valueMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label",   e.getKey());
                    m.put("percent", round1((e.getValue() / totalValue) * 100));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildEvolution(List<Position> positions) {
        if (positions.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        // Parse dates once — skip positions with invalid dates
        Map<String, LocalDate> parsedDates = new LinkedHashMap<>();
        for (Position p : positions) {
            try { parsedDates.put(p.getId(), LocalDate.parse(p.getPurchaseDate())); }
            catch (Exception ignored) { /* skip malformed dates */ }
        }
        if (parsedDates.isEmpty()) return List.of();

        LocalDate earliest = parsedDates.values().stream()
                .min(Comparator.naturalOrder())
                .orElse(today.minusMonths(1));

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cursor = earliest.withDayOfMonth(1);
        LocalDate end    = today.withDayOfMonth(1);

        while (!cursor.isAfter(end)) {
            final LocalDate monthEnd = cursor.plusMonths(1).minusDays(1);
            double monthValue = 0;

            for (Position pos : positions) {
                LocalDate purchaseDate = parsedDates.get(pos.getId());
                if (purchaseDate == null || purchaseDate.isAfter(monthEnd)) continue;

                long totalDays   = ChronoUnit.DAYS.between(purchaseDate, today);
                long elapsedDays = ChronoUnit.DAYS.between(purchaseDate, cursor.isAfter(purchaseDate) ? cursor : purchaseDate);
                elapsedDays = Math.max(0, Math.min(elapsedDays, totalDays));

                double progress = totalDays == 0 ? 1.0 : (double) elapsedDays / totalDays;
                double price    = pos.getPurchasePrice() + (pos.getCurrentPrice() - pos.getPurchasePrice()) * progress;
                monthValue     += pos.getQuantity() * price;
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", cursor.format(fmt));
            point.put("value", round2(monthValue));
            result.add(point);

            cursor = cursor.plusMonths(1);
        }

        return result;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }
}
