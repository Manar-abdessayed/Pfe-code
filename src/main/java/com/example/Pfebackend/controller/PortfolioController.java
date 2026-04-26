package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.model.Transaction;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.PositionRepository;
import com.example.Pfebackend.repository.TransactionRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public PortfolioController(PositionRepository positionRepository,
                               TransactionRepository transactionRepository,
                               UserRepository userRepository) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // ─── GET /api/portfolio/instruments ─────────────────────────────────────────
    @GetMapping("/instruments")
    public ResponseEntity<List<Map<String, Object>>> getTradingInstruments(
            @RequestParam(required = false) String q) {

        List<Map<String, Object>> result;

        String baseSelect =
            "SELECT d.isin, d.short_name, d.full_name, d.currency, " +
            "f.close_price, f.price_variation_pct, f.volume " +
            "FROM dim_instrument d " +
            "INNER JOIN fact_ohlcv_daily f ON d.isin = f.isin AND f.session_date = (" +
            "  SELECT MAX(session_date) FROM fact_ohlcv_daily WHERE isin = d.isin" +
            ") " +
            "WHERE d.is_active = 1 AND f.close_price IS NOT NULL AND f.close_price > 0 ";

        if (q != null && q.trim().length() >= 2) {
            String search = "%" + q.trim().toUpperCase() + "%";
            result = jdbcTemplate.queryForList(
                "SELECT TOP 20 * FROM (" + baseSelect +
                "AND (UPPER(d.short_name) LIKE ? OR UPPER(d.full_name) LIKE ?)" +
                ") sub ORDER BY volume DESC",
                search, search
            );
        } else {
            result = jdbcTemplate.queryForList(
                "SELECT TOP 50 * FROM (" + baseSelect + ") sub ORDER BY volume DESC"
            );
        }

        return ResponseEntity.ok(result);
    }

    // ─── GET /api/portfolio/analysis/{symbol} ───────────────────────────────────
    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<Map<String, Object>> getPositionAnalysis(@PathVariable String symbol) {

        List<Map<String, Object>> instruments = jdbcTemplate.queryForList(
            "SELECT TOP 1 isin FROM dim_instrument WHERE short_name = ? AND is_active = 1",
            symbol
        );

        if (instruments.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        String isin = instruments.get(0).get("isin").toString();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT TOP 1 rsi_14, macd, bb_upper, bb_lower, sma_20, sma_50, " +
            "signal_rsi, signal_macd, signal_bb, daily_return_pct, volatility_20d " +
            "FROM fact_technical_indicators WHERE isin = ? ORDER BY session_date DESC",
            isin
        );

        if (rows.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        Map<String, Object> t = rows.get(0);
        String sRsi  = String.valueOf(t.getOrDefault("signal_rsi",  "Conserver"));
        String sMacd = String.valueOf(t.getOrDefault("signal_macd", "Conserver"));
        String sBb   = String.valueOf(t.getOrDefault("signal_bb",   "Conserver"));

        int buyCount  = 0;
        int sellCount = 0;
        for (String s : new String[]{sRsi, sMacd, sBb}) {
            if ("Achat".equalsIgnoreCase(s))  buyCount++;
            if ("Vente".equalsIgnoreCase(s))  sellCount++;
        }

        String globalSignal, signalLabel, recommendation;
        if (buyCount >= 2)       { globalSignal = "Achat";     signalLabel = "Haussier";          recommendation = "Acheter";  }
        else if (sellCount >= 2) { globalSignal = "Vente";     signalLabel = "Baissier";          recommendation = "Vendre";   }
        else if (buyCount == 1)  { globalSignal = "Conserver"; signalLabel = "Neutre à haussier"; recommendation = "Maintenir"; }
        else                     { globalSignal = "Conserver"; signalLabel = "Neutre à baissier"; recommendation = "Maintenir"; }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available",      true);
        result.put("globalSignal",   globalSignal);
        result.put("signalLabel",    signalLabel);
        result.put("recommendation", recommendation);
        result.put("support",        t.get("bb_lower"));
        result.put("resistance",     t.get("bb_upper"));
        result.put("rsi",            t.get("rsi_14"));
        result.put("macd",           t.get("macd"));
        result.put("sma20",          t.get("sma_20"));
        result.put("sma50",          t.get("sma_50"));
        result.put("volatility",     t.get("volatility_20d"));
        result.put("dailyReturn",    t.get("daily_return_pct"));

        return ResponseEntity.ok(result);
    }

    // ─── GET /api/portfolio/{userId} ────────────────────────────────────────────
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable String userId) {
        return ResponseEntity.ok(buildPortfolioResponse(userId));
    }

    // ─── POST /api/portfolio/{userId}/buy ────────────────────────────────────────
    @PostMapping("/{userId}/buy")
    public ResponseEntity<Map<String, Object>> buyStock(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        String symbol      = request.get("symbol").toString();
        String companyName = request.get("companyName").toString();
        double quantity    = Double.parseDouble(request.get("quantity").toString());
        double price       = Double.parseDouble(request.get("price").toString());
        String sector      = request.getOrDefault("sector", "Autres").toString();
        String assetClass  = request.getOrDefault("assetClass", "Actions").toString();
        String today       = LocalDate.now().toString();

        if (quantity <= 0 || price <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Quantité et prix doivent être positifs."));
        }

        double totalCost = round2(quantity * price);

        // Budget check
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Utilisateur non trouvé."));
        }
        User user = userOpt.get();
        if (user.getAvailableCapital() < totalCost - 1e-9) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", String.format(
                    "Budget insuffisant. Disponible : %.2f € — Requis : %.2f €",
                    user.getAvailableCapital(), totalCost)
            ));
        }

        // Merge with existing position for same symbol
        List<Position> positions = positionRepository.findByUserId(userId);
        Optional<Position> existing = positions.stream()
                .filter(p -> p.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();

        if (existing.isPresent()) {
            Position pos = existing.get();
            double newQty      = pos.getQuantity() + quantity;
            double newAvgPrice = (pos.getQuantity() * pos.getPurchasePrice() + quantity * price) / newQty;
            pos.setQuantity(round4(newQty));
            pos.setPurchasePrice(round2(newAvgPrice));
            pos.setCurrentPrice(round2(price));
            positionRepository.save(pos);
        } else {
            Position pos = new Position();
            pos.setUserId(userId);
            pos.setSymbol(symbol.toUpperCase());
            pos.setCompanyName(companyName);
            pos.setQuantity(round4(quantity));
            pos.setPurchasePrice(round2(price));
            pos.setCurrentPrice(round2(price));
            pos.setSector(sector);
            pos.setAssetClass(assetClass);
            pos.setPurchaseDate(today);
            positionRepository.save(pos);
        }

        // Deduct from available capital
        user.setAvailableCapital(round2(user.getAvailableCapital() - totalCost));
        userRepository.save(user);

        // Record transaction
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("BUY");
        tx.setSymbol(symbol.toUpperCase());
        tx.setCompanyName(companyName);
        tx.setQuantity(round4(quantity));
        tx.setPrice(round2(price));
        tx.setTotalAmount(totalCost);
        tx.setTransactionDate(today);
        transactionRepository.save(tx);

        return ResponseEntity.ok(buildPortfolioResponse(userId));
    }

    // ─── POST /api/portfolio/{userId}/sell ───────────────────────────────────────
    @PostMapping("/{userId}/sell")
    public ResponseEntity<Map<String, Object>> sellStock(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        String positionId = request.get("positionId").toString();
        double quantity   = Double.parseDouble(request.get("quantity").toString());
        double price      = Double.parseDouble(request.get("price").toString());

        if (quantity <= 0 || price <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Quantité et prix doivent être positifs."));
        }

        Optional<Position> opt = positionRepository.findByIdAndUserId(positionId, userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Position non trouvée."));
        }

        Position pos = opt.get();
        if (quantity > pos.getQuantity() + 1e-9) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Quantité à vendre supérieure à la position."));
        }

        double sellProceeds = round2(quantity * price);

        // Credit proceeds back to available capital
        userRepository.findById(userId).ifPresent(u -> {
            u.setAvailableCapital(round2(u.getAvailableCapital() + sellProceeds));
            userRepository.save(u);
        });

        // Record transaction before modifying position
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("SELL");
        tx.setSymbol(pos.getSymbol());
        tx.setCompanyName(pos.getCompanyName());
        tx.setQuantity(round4(quantity));
        tx.setPrice(round2(price));
        tx.setTotalAmount(sellProceeds);
        tx.setTransactionDate(LocalDate.now().toString());
        transactionRepository.save(tx);

        double remaining = pos.getQuantity() - quantity;
        if (remaining <= 1e-9) {
            positionRepository.deleteById(positionId);
        } else {
            pos.setQuantity(round4(remaining));
            pos.setCurrentPrice(round2(price));
            positionRepository.save(pos);
        }

        return ResponseEntity.ok(buildPortfolioResponse(userId));
    }

    // ─── GET /api/portfolio/{userId}/transactions ────────────────────────────────
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<Map<String, Object>>> getTransactions(@PathVariable String userId) {
        List<Transaction> txs = transactionRepository.findByUserIdOrderByTransactionDateDesc(userId);
        List<Map<String, Object>> result = txs.stream().map(tx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",              tx.getId());
            m.put("type",            tx.getType());
            m.put("symbol",          tx.getSymbol());
            m.put("companyName",     tx.getCompanyName());
            m.put("quantity",        tx.getQuantity());
            m.put("price",           tx.getPrice());
            m.put("totalAmount",     tx.getTotalAmount());
            m.put("transactionDate", tx.getTransactionDate());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Position non trouvée."));
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

    // ─── Shared portfolio builder ─────────────────────────────────────────────────

    private Map<String, Object> buildPortfolioResponse(String userId) {
        List<Position> positions = positionRepository.findByUserId(userId);

        double totalValue = 0;
        double totalCost  = 0;
        for (Position p : positions) {
            totalValue += p.getQuantity() * p.getCurrentPrice();
            totalCost  += p.getQuantity() * p.getPurchasePrice();
        }
        final double tv = totalValue;
        double totalPL        = tv - totalCost;
        double totalPLPercent = totalCost > 0 ? (totalPL / totalCost) * 100 : 0;

        double availableCapital = userRepository.findById(userId)
                .map(User::getAvailableCapital)
                .orElse(0.0);

        List<Map<String, Object>> positionItems       = positions.stream()
                .map(p -> toPositionItem(p, tv)).collect(Collectors.toList());
        List<Map<String, Object>> sectorBreakdown     = buildBreakdown(positions, Position::getSector, tv);
        List<Map<String, Object>> assetClassBreakdown = buildBreakdown(positions, Position::getAssetClass, tv);
        List<Map<String, Object>> evolutionData       = buildEvolution(positions);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("positions",           positionItems);
        response.put("totalValue",          round2(totalValue));
        response.put("totalCost",           round2(totalCost));
        response.put("totalPL",             round2(totalPL));
        response.put("totalPLPercent",      round2(totalPLPercent));
        response.put("availableCapital",    round2(availableCapital));
        response.put("sectorBreakdown",     sectorBreakdown);
        response.put("assetClassBreakdown", assetClassBreakdown);
        response.put("evolutionData",       evolutionData);
        return response;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Position fromRequest(Map<String, Object> req, String userId) {
        Position pos = new Position();
        pos.setUserId(userId);
        applyRequest(pos, req);
        return pos;
    }

    private void applyRequest(Position pos, Map<String, Object> req) {
        if (req.containsKey("symbol"))        pos.setSymbol(req.get("symbol").toString());
        if (req.containsKey("companyName"))   pos.setCompanyName(req.get("companyName").toString());
        if (req.containsKey("sector"))        pos.setSector(req.get("sector").toString());
        if (req.containsKey("assetClass"))    pos.setAssetClass(req.get("assetClass").toString());
        if (req.containsKey("purchaseDate"))  pos.setPurchaseDate(req.get("purchaseDate").toString());
        if (req.containsKey("quantity"))      pos.setQuantity(Double.parseDouble(req.get("quantity").toString()));
        if (req.containsKey("purchasePrice")) pos.setPurchasePrice(Double.parseDouble(req.get("purchasePrice").toString()));
        if (req.containsKey("currentPrice"))  pos.setCurrentPrice(Double.parseDouble(req.get("currentPrice").toString()));
    }

    private Map<String, Object> toPositionItem(Position pos, double totalValue) {
        double value     = pos.getQuantity() * pos.getCurrentPrice();
        double cost      = pos.getQuantity() * pos.getPurchasePrice();
        double pl        = value - cost;
        double plPercent = cost > 0 ? (pl / cost) * 100 : 0;
        double weight    = totalValue > 0 ? (value / totalValue) * 100 : 0;

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

        Map<String, LocalDate> parsedDates = new LinkedHashMap<>();
        for (Position p : positions) {
            try { parsedDates.put(p.getId(), LocalDate.parse(p.getPurchaseDate())); }
            catch (Exception ignored) {}
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
                long elapsedDays = ChronoUnit.DAYS.between(purchaseDate,
                        cursor.isAfter(purchaseDate) ? cursor : purchaseDate);
                elapsedDays = Math.max(0, Math.min(elapsedDays, totalDays));

                double progress = totalDays == 0 ? 1.0 : (double) elapsedDays / totalDays;
                double price    = pos.getPurchasePrice()
                        + (pos.getCurrentPrice() - pos.getPurchasePrice()) * progress;
                monthValue += pos.getQuantity() * price;
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
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
