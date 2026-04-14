package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ─────────────────────────────────────────
    // POST /api/auth/register
    // Body: { email, password, firstName, lastName, phoneNumber?, role?, riskTolerance?, investmentGoal? }
    // ─────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");

        // Vérifier email déjà utilisé
        if (userRepository.existsByEmail(email)) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Cet email est déjà utilisé.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Créer et sauvegarder l'utilisateur
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));  // Mot de passe hashé
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneNumber(request.get("phoneNumber"));
        user.setRole(request.getOrDefault("role", "USER"));
        user.setRiskTolerance(request.get("riskTolerance"));
        user.setInvestmentGoal(request.get("investmentGoal"));

        User savedUser = userRepository.save(user);

        // Générer le token JWT
        String token = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId());

        // Retourner la réponse attendue par Angular (AuthResponse)
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("id", savedUser.getId());
        response.put("email", savedUser.getEmail());
        response.put("firstName", savedUser.getFirstName());
        response.put("lastName", savedUser.getLastName());
        response.put("role", savedUser.getRole());
        response.put("riskTolerance", savedUser.getRiskTolerance());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────
    // POST /api/auth/login
    // Body: { email, password }
    // ─────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty() || !passwordEncoder.matches(password, optionalUser.get().getPassword())) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Email ou mot de passe incorrect.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        User user = optionalUser.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("role", user.getRole());
        response.put("riskTolerance", user.getRiskTolerance());

        return ResponseEntity.ok(response);
    }
}
