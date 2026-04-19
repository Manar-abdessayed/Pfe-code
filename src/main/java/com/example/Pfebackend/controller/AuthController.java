package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Admin;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.AdminRepository;
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
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          AdminRepository adminRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
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
}
