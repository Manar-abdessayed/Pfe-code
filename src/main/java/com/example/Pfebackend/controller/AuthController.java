package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Admin;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.AdminRepository;
import com.example.Pfebackend.repository.UserRepository;
import com.example.Pfebackend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository,
                          AdminRepository adminRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String email     = request.get("email");
        String password  = request.get("password");
        String firstName = request.get("firstName");
        String lastName  = request.get("lastName");

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Cet email est déjà utilisé."));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneNumber(request.get("phoneNumber"));
        user.setRole(request.getOrDefault("role", "USER"));
        user.setRiskTolerance(request.get("riskTolerance"));
        user.setInvestmentGoal(request.get("investmentGoal"));

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token",         token);
        response.put("id",            saved.getId());
        response.put("email",         saved.getEmail());
        response.put("firstName",     saved.getFirstName());
        response.put("lastName",      saved.getLastName());
        response.put("role",          saved.getRole());
        response.put("riskTolerance", saved.getRiskTolerance());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email    = request.get("email");
        String password = request.get("password");

        // Check admin collection first
        Optional<Admin> optAdmin = adminRepository.findByEmail(email);
        if (optAdmin.isPresent() && passwordEncoder.matches(password, optAdmin.get().getPassword())) {
            Admin admin  = optAdmin.get();
            String token = jwtUtil.generateToken(admin.getEmail(), admin.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("token",     token);
            response.put("id",        admin.getId());
            response.put("email",     admin.getEmail());
            response.put("firstName", admin.getFirstName());
            response.put("lastName",  admin.getLastName());
            response.put("role",      "ADMIN");
            return ResponseEntity.ok(response);
        }

        // Then check regular users
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isPresent() && passwordEncoder.matches(password, optUser.get().getPassword())) {
            User user    = optUser.get();
            String token = jwtUtil.generateToken(user.getEmail(), user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("token",         token);
            response.put("id",            user.getId());
            response.put("email",         user.getEmail());
            response.put("firstName",     user.getFirstName());
            response.put("lastName",      user.getLastName());
            response.put("role",          user.getRole());
            response.put("riskTolerance", user.getRiskTolerance());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Email ou mot de passe incorrect."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            // Always return success to avoid user enumeration
            return ResponseEntity.ok(Map.of("message", "Si cet email existe, un lien a été envoyé."));
        }

        User user = optUser.get();
        String token = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + 3_600_000; // 1 heure

        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiry(expiry);
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(email, token);
        } catch (Exception e) {
            log.error("Email sending failed for {}: {}", email, e.getMessage());
            log.warn(">>> [DEV] Reset link: http://localhost:4200/reset-password?token={}", token);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'envoi de l'email : " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("message", "Si cet email existe, un lien a été envoyé."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String token       = request.get("token");
        String newPassword = request.get("newPassword");

        if (token == null || newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Données invalides."));
        }

        Optional<User> optUser = userRepository.findByResetPasswordToken(token);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Token invalide ou expiré."));
        }

        User user = optUser.get();
        if (user.getResetPasswordTokenExpiry() == null ||
                System.currentTimeMillis() > user.getResetPasswordTokenExpiry()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Token invalide ou expiré."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour avec succès."));
    }
}
