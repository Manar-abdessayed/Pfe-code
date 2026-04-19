package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.NotificationPrefs;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SettingsController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getSettings(@PathVariable String userId) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toSettingsResponse(opt.get()));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable String userId,
                                           @RequestBody Map<String, Object> req) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User user = opt.get();
        String newEmail = req.containsKey("email") ? req.get("email").toString() : null;

        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(newEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Cet email est déjà utilisé."));
        }

        if (req.containsKey("firstName"))  user.setFirstName(req.get("firstName").toString());
        if (req.containsKey("lastName"))   user.setLastName(req.get("lastName").toString());
        if (newEmail != null)              user.setEmail(newEmail);
        if (req.containsKey("phoneNumber")) user.setPhoneNumber(req.get("phoneNumber").toString());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toSettingsResponse(saved));
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<?> changePassword(@PathVariable String userId,
                                            @RequestBody Map<String, Object> req) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User user = opt.get();
        String currentPassword = req.getOrDefault("currentPassword", "").toString();
        String newPassword     = req.getOrDefault("newPassword", "").toString();

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Mot de passe actuel incorrect."));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le nouveau mot de passe doit contenir au moins 6 caractères."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    @PutMapping("/{userId}/notifications")
    public ResponseEntity<?> updateNotifications(@PathVariable String userId,
                                                 @RequestBody Map<String, Object> req) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User user = opt.get();
        NotificationPrefs prefs = user.getNotificationPrefs();
        if (prefs == null) prefs = new NotificationPrefs();

        if (req.containsKey("emailNotifications"))
            prefs.setEmailNotifications(Boolean.parseBoolean(req.get("emailNotifications").toString()));
        if (req.containsKey("marketAlerts"))
            prefs.setMarketAlerts(Boolean.parseBoolean(req.get("marketAlerts").toString()));
        if (req.containsKey("portfolioAlerts"))
            prefs.setPortfolioAlerts(Boolean.parseBoolean(req.get("portfolioAlerts").toString()));

        user.setNotificationPrefs(prefs);
        userRepository.save(user);
        return ResponseEntity.ok(prefs);
    }

    private Map<String, Object> toSettingsResponse(User user) {
        NotificationPrefs prefs = user.getNotificationPrefs() != null
                ? user.getNotificationPrefs() : new NotificationPrefs();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",          user.getId());
        resp.put("firstName",   user.getFirstName());
        resp.put("lastName",    user.getLastName());
        resp.put("email",       user.getEmail());
        resp.put("phoneNumber", user.getPhoneNumber());
        resp.put("role",        user.getRole());
        resp.put("emailNotifications", prefs.isEmailNotifications());
        resp.put("marketAlerts",       prefs.isMarketAlerts());
        resp.put("portfolioAlerts",    prefs.isPortfolioAlerts());
        return resp;
    }
}
