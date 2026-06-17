package com.example.Pfebackend.service;

import com.example.Pfebackend.dto.portfolio.*;
import com.example.Pfebackend.model.Position;
import com.example.Pfebackend.model.Transaction;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.PositionRepository;
import com.example.Pfebackend.repository.TransactionRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public PortfolioService(PositionRepository positionRepository,
                            TransactionRepository transactionRepository,
                            UserRepository userRepository,
                            JdbcTemplate jdbcTemplate) {
        this.positionRepository    = positionRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository        = userRepository;
        this.jdbcTemplate          = jdbcTemplate;
    }

    public List<Map<String, Object>> getTradingInstruments(String q) {
        String baseSelect =
            "SELECT d.isin, d.short_name, d.full_name, d.currency, " +
            "f.close_price, f.price_variation_pct, f.volume " +
            "FROM dim_instrument d " +
            "INNER JOIN fact_ohlcv_daily f ON d.isin = f.isin AND f.session_date = (" +
            "  SELECT MAX(session_date) FROM fact_ohlcv_daily WHERE isin = d.isin" +
            ") " +
            "WHERE d.is_active = 1 AND f.close_price IS NOT NULL AND f.close_price > 0 ";

        if (q != null && q.trim().length() >= 2) {
            String pattern = "%" + q.trim().toUpperCase() + "%";
            return jdbcTemplate.queryForList(
                "SELECT TOP 20 * FROM (" + baseSelect +
                "AND (UPPER(d.short_name) LIKE ? OR UPPER(d.full_name) LIKE ?)) sub ORDER BY volume DESC",
                pattern, pattern);
        }
        return jdbcTemplate.queryForList(
            "SELECT TOP 50 * FROM (" + baseSelect + ") sub ORDER BY volume DESC");
    }

    public Map<String, Object> getPositionAnalysis(String symbol) {
        List<Map<String, Object>> instruments = jdbcTemplate.queryForList(
            "SELECT TOP 1 isin FROM dim_instrument WHERE short_name = ? AND is_active = 1", symbol);

        if (instruments.isEmpty()) return Map.of("available", false);

        String isin = instruments.get(0).get("isin").toString();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT TOP 1 rsi_14, macd, bb_upper, bb_lower, sma_20, sma_50, " +
            "signal_rsi, signal_macd, signal_bb, daily_return_pct, volatility_20d " +
            "FROM fact_technical_indicators WHERE isin = ? ORDER BY session_date DESC", isin);

        if (rows.isEmpty()) return Map.of("available", false);

        Map<String, Object> t    = rows.get(0);
        String sRsi  = String.valueOf(t.getOrDefault("signal_rsi",  "Conserver"));
        String sMacd = String.valueOf(t.getOrDefault("signal_macd", "Conserver"));
        String sBb   = String.valueOf(t.getOrDefault("signal_bb",   "Conserver"));

        int buy = 0, sell = 0;
        for (String s : new String[]{sRsi, sMacd, sBb}) {
            if ("Achat".equalsIgnoreCase(s)) buy++;
            if ("Vente".equalsIgnoreCase(s)) sell++;
        }

        String globalSignal, signalLabel, recommendation;
        if (buy >= 2)       { globalSignal = "Achat";     signalLabel = "Haussier";          recommendation = "Acheter";  }
        else if (sell >= 2) { globalSignal = "Vente";     signalLabel = "Baissier";          recommendation = "Vendre";   }
        else if (buy == 1)  { globalSignal = "Conserver"; signalLabel = "Neutre à haussier"; recommendation = "Maintenir"; }
        else                { globalSignal = "Conserver"; signalLabel = "Neutre à baissier"; recommendation = "Maintenir"; }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("globalSignal", globalSignal);
        result.put("signalLabel", signalLabel);
        result.put("recommendation", recommendation);
        result.put("support",     t.get("bb_lower"));
        result.put("resistance",  t.get("bb_upper"));
        result.put("rsi",         t.get("rsi_14"));
        result.put("macd",        t.get("macd"));
        result.put("sma20",       t.get("sma_20"));
        result.put("sma50",       t.get("sma_50"));
        result.put("volatility",  t.get("volatility_20d"));
        result.put("dailyReturn", t.get("daily_return_pct"));
        return result;
    }

    public PortfolioResponse getPortfolio(String userId) {
        return buildPortfolioResponse(userId);
    }

    public PortfolioResponse buy(String userId, BuyRequest request) {
        if (request.quantity() <= 0 || request.price() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantité et prix doivent être positifs.");
        }

        double totalCost = round2(request.quantity() * request.price());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé."));

        if (user.getAvailableCapital() < totalCost - 1e-9) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Budget insuffisant. Disponible : %.2f € — Requis : %.2f €",
                    user.getAvailableCapital(), totalCost));
        }

        List<Position> positions = positionRepository.findByUserId(userId);
        Optional<Position> existing = positions.stream()
                .filter(p -> p.getSymbol().equalsIgnoreCase(request.symbol()))
                .findFirst();

        if (existing.isPresent()) {
            Position pos = existing.get();
            double newQty      = pos.getQuantity() + request.quantity();
            double newAvgPrice = (pos.getQuantity() * pos.getPurchasePrice() + request.quantity() * request.price()) / newQty;
            pos.setQuantity(round4(newQty));
            pos.setPurchasePrice(round2(newAvgPrice));
            pos.setCurrentPrice(round2(request.price()));
            positionRepository.save(pos);
        } else {
            Position pos = new Position();
            pos.setUserId(userId);
            pos.setSymbol(request.symbol().toUpperCase());
            pos.setCompanyName(request.companyName());
            pos.setQuantity(round4(request.quantity()));
            pos.setPurchasePrice(round2(request.price()));
            pos.setCurrentPrice(round2(request.price()));
            pos.setSector(request.sector() != null ? request.sector() : "Autres");
            pos.setAssetClass(request.assetClass() != null ? request.assetClass() : "Actions");
            pos.setPurchaseDate(LocalDate.now().toString());
            positionRepository.save(pos);
        }

        user.setAvailableCapital(round2(user.getAvailableCapital() - totalCost));
        userRepository.save(user);

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("BUY");
        tx.setSymbol(request.symbol().toUpperCase());
        tx.setCompanyName(request.companyName());
        tx.setQuantity(round4(request.quantity()));
        tx.setPrice(round2(request.price()));
        tx.setTotalAmount(totalCost);
        tx.setTransactionDate(LocalDate.now().toString());
        transactionRepository.save(tx);

        return buildPortfolioResponse(userId);
    }

    public PortfolioResponse sell(String userId, SellRequest request) {
        if (request.quantity() <= 0 || request.price() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantité et prix doivent être positifs.");
        }

        Position pos = positionRepository.findByIdAndUserId(request.positionId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position non trouvée."));

        if (request.quantity() > pos.getQuantity() + 1e-9) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantité à vendre supérieure à la position.");
        }

        double sellProceeds = round2(request.quantity() * request.price());

        userRepository.findById(userId).ifPresent(u -> {
            u.setAvailableCapital(round2(u.getAvailableCapital() + sellProceeds));
            userRepository.save(u);
        });

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("SELL");
        tx.setSymbol(pos.getSymbol());
        tx.setCompanyName(pos.getCompanyName());
        tx.setQuantity(round4(request.quantity()));
        tx.setPrice(round2(request.price()));
        tx.setTotalAmount(sellProceeds);
        tx.setTransactionDate(LocalDate.now().toString());
        transactionRepository.save(tx);

        double remaining = pos.getQuantity() - request.quantity();
        if (remaining <= 1e-9) {
            positionRepository.deleteById(request.positionId());
        } else {
            pos.setQuantity(round4(remaining));
            pos.setCurrentPrice(round2(request.price()));
            positionRepository.save(pos);
        }

        return buildPortfolioResponse(userId);
    }

    public List<TransactionResponse> getTransactions(String userId) {
        return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId).stream()
                .map(tx -> new TransactionResponse(tx.getId(), tx.getType(), tx.getSymbol(),
                        tx.getCompanyName(), tx.getQuantity(), tx.getPrice(),
                        tx.getTotalAmount(), tx.getTransactionDate()))
                .collect(Collectors.toList());
    }

    public PositionResponse addPosition(String userId, PositionRequest request) {
        Position pos = applyRequest(new Position(), request);
        pos.setUserId(userId);
        Position saved = positionRepository.save(pos);
        double totalValue = positionRepository.findByUserId(userId).stream()
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        return toPositionResponse(saved, totalValue);
    }

    public PositionResponse updatePosition(String userId, String positionId, PositionRequest request) {
        Position pos = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position non trouvée."));
        applyRequest(pos, request);
        Position saved = positionRepository.save(pos);
        double totalValue = positionRepository.findByUserId(userId).stream()
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        return toPositionResponse(saved, totalValue);
    }

    public void deletePosition(String userId, String positionId) {
        positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position non trouvée."));
        positionRepository.deleteById(positionId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private PortfolioResponse buildPortfolioResponse(String userId) {
        List<Position> positions = positionRepository.findByUserId(userId);

        double totalValue = 0, totalCost = 0;
        for (Position p : positions) {
            totalValue += p.getQuantity() * p.getCurrentPrice();
            totalCost  += p.getQuantity() * p.getPurchasePrice();
        }
        final double tv = totalValue;
        double totalPL        = tv - totalCost;
        double totalPLPercent = totalCost > 0 ? (totalPL / totalCost) * 100 : 0;

        double availableCapital = userRepository.findById(userId)
                .map(User::getAvailableCapital).orElse(0.0);

        List<PositionResponse>  posItems   = positions.stream().map(p -> toPositionResponse(p, tv)).collect(Collectors.toList());
        List<BreakdownItem>     sectors    = buildBreakdown(positions, Position::getSector, tv);
        List<BreakdownItem>     assetClass = buildBreakdown(positions, Position::getAssetClass, tv);
        List<EvolutionPoint>    evolution  = buildEvolution(positions);

        return new PortfolioResponse(posItems, round2(totalValue), round2(totalCost),
                round2(totalPL), round2(totalPLPercent), round2(availableCapital),
                sectors, assetClass, evolution);
    }

    private Position applyRequest(Position pos, PositionRequest req) {
        if (req.symbol() != null)        pos.setSymbol(req.symbol());
        if (req.companyName() != null)   pos.setCompanyName(req.companyName());
        if (req.sector() != null)        pos.setSector(req.sector());
        if (req.assetClass() != null)    pos.setAssetClass(req.assetClass());
        if (req.purchaseDate() != null)  pos.setPurchaseDate(req.purchaseDate());
        pos.setQuantity(req.quantity());
        pos.setPurchasePrice(req.purchasePrice());
        pos.setCurrentPrice(req.currentPrice());
        return pos;
    }

    private PositionResponse toPositionResponse(Position pos, double totalValue) {
        double value     = pos.getQuantity() * pos.getCurrentPrice();
        double cost      = pos.getQuantity() * pos.getPurchasePrice();
        double pl        = value - cost;
        double plPercent = cost > 0 ? (pl / cost) * 100 : 0;
        double weight    = totalValue > 0 ? (value / totalValue) * 100 : 0;
        return new PositionResponse(pos.getId(), pos.getUserId(), pos.getSymbol(), pos.getCompanyName(),
                pos.getQuantity(), pos.getPurchasePrice(), pos.getCurrentPrice(),
                pos.getSector(), pos.getAssetClass(), pos.getPurchaseDate(),
                round2(value), round2(pl), round2(plPercent), round1(weight));
    }

    private List<BreakdownItem> buildBreakdown(List<Position> positions,
                                               Function<Position, String> fieldGetter, double totalValue) {
        if (totalValue == 0) return List.of();
        Map<String, Double> map = new LinkedHashMap<>();
        for (Position p : positions) {
            String key = fieldGetter.apply(p);
            if (key == null) key = "Autres";
            map.merge(key, p.getQuantity() * p.getCurrentPrice(), Double::sum);
        }
        return map.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> new BreakdownItem(e.getKey(), round1((e.getValue() / totalValue) * 100)))
                .collect(Collectors.toList());
    }

    private List<EvolutionPoint> buildEvolution(List<Position> positions) {
        if (positions.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter monthFmt   = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, LocalDate> parsedDates = new LinkedHashMap<>();
        for (Position p : positions) {
            try { parsedDates.put(p.getId(), LocalDate.parse(p.getPurchaseDate())); }
            catch (Exception ignored) {}
        }
        if (parsedDates.isEmpty()) return List.of();

        LocalDate earliest  = parsedDates.values().stream().min(Comparator.naturalOrder()).orElse(today.minusDays(30));
        LocalDate startDate = earliest.isBefore(today.minusDays(90)) ? today.minusDays(90) : earliest;

        List<EvolutionPoint> result = new ArrayList<>();
        LocalDate cursor = startDate;

        while (!cursor.isAfter(today)) {
            final LocalDate day = cursor;
            double dayValue = 0;
            for (Position pos : positions) {
                LocalDate purchaseDate = parsedDates.get(pos.getId());
                if (purchaseDate == null || purchaseDate.isAfter(day)) continue;
                long totalDays   = ChronoUnit.DAYS.between(purchaseDate, today);
                long elapsedDays = Math.max(0, Math.min(ChronoUnit.DAYS.between(purchaseDate, day), totalDays));
                double progress  = totalDays == 0 ? 1.0 : (double) elapsedDays / totalDays;
                double price     = pos.getPurchasePrice() + (pos.getCurrentPrice() - pos.getPurchasePrice()) * progress;
                dayValue += pos.getQuantity() * price;
            }
            result.add(new EvolutionPoint(day.format(displayFmt), day.format(monthFmt), round2(dayValue)));
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private double round2(double v) { return Math.round(v * 100.0)   / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)    / 10.0;  }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
