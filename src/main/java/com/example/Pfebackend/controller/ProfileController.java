package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // GET /api/profile/{userId}
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable String userId) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilisateur non trouvé."));
        }
        return ResponseEntity.ok(toProfileResponse(opt.get()));
    }

    // PUT /api/profile/{userId}
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilisateur non trouvé."));
        }

        User user = opt.get();

        if (request.containsKey("riskLevel")) {
            int level = Integer.parseInt(request.get("riskLevel").toString());
            user.setRiskLevel(level);
            user.setRiskTolerance(computeRiskLabel(level));
        }
        if (request.containsKey("investmentGoal")) {
            user.setInvestmentGoal(request.get("investmentGoal").toString());
        }
        if (request.containsKey("investmentHorizon")) {
            user.setInvestmentHorizon(request.get("investmentHorizon").toString());
        }
        if (request.containsKey("availableCapital")) {
            user.setAvailableCapital(Double.parseDouble(request.get("availableCapital").toString()));
        }
        if (request.containsKey("sectors")) {
            @SuppressWarnings("unchecked")
            List<String> sectors = (List<String>) request.get("sectors");
            user.setSectors(sectors);
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toProfileResponse(saved));
    }

    private Map<String, Object> toProfileResponse(User user) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", user.getId());
        resp.put("firstName", user.getFirstName());
        resp.put("lastName", user.getLastName());
        resp.put("email", user.getEmail());
        resp.put("riskLevel", user.getRiskLevel());
        resp.put("riskTolerance", user.getRiskTolerance());
        resp.put("investmentGoal", user.getInvestmentGoal());
        resp.put("investmentHorizon", user.getInvestmentHorizon());
        resp.put("availableCapital", user.getAvailableCapital());
        resp.put("sectors", user.getSectors());
        return resp;
    }

    private String computeRiskLabel(int level) {
        if (level <= 3) return "Prudent";
        if (level <= 6) return "Modéré";
        return "Agressif";
    }
}
