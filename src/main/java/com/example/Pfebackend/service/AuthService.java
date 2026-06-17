package com.example.Pfebackend.service;

import com.example.Pfebackend.controller.JwtUtil;
import com.example.Pfebackend.dto.auth.*;
import com.example.Pfebackend.dto.common.MessageResponse;
import com.example.Pfebackend.model.Admin;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.AdminRepository;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository  = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil         = jwtUtil;
        this.emailService    = emailService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé.");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role() != null ? request.role() : "USER");
        user.setRiskTolerance(request.riskTolerance());
        user.setInvestmentGoal(request.investmentGoal());
        user.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getId());

        return new AuthResponse(token, saved.getId(), saved.getEmail(),
                saved.getFirstName(), saved.getLastName(), saved.getRole(), saved.getRiskTolerance());
    }

    public AuthResponse login(LoginRequest request) {
        AdminRepository.class.getName(); // force eager check

        // Check admin first
        Admin admin = adminRepository.findByEmail(request.email()).orElse(null);
        if (admin != null && passwordEncoder.matches(request.password(), admin.getPassword())) {
            String token = jwtUtil.generateToken(admin.getEmail(), admin.getId());
            return new AuthResponse(token, admin.getId(), admin.getEmail(),
                    admin.getFirstName(), admin.getLastName(), "ADMIN", null);
        }

        // Check regular user
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user != null && passwordEncoder.matches(request.password(), user.getPassword())) {
            String token = jwtUtil.generateToken(user.getEmail(), user.getId());
            return new AuthResponse(token, user.getId(), user.getEmail(),
                    user.getFirstName(), user.getLastName(), user.getRole(), user.getRiskTolerance());
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect.");
    }

    public MessageResponse forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return new MessageResponse("Si cet email existe, un lien a été envoyé.");
        }

        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiry(System.currentTimeMillis() + 3_600_000);
        userRepository.save(user);

        emailService.sendPasswordResetEmail(email, token);
        return new MessageResponse("Si cet email existe, un lien a été envoyé.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (request.token() == null || request.newPassword() == null || request.newPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Données invalides.");
        }

        User user = userRepository.findByResetPasswordToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expiré."));

        if (user.getResetPasswordTokenExpiry() == null
                || System.currentTimeMillis() > user.getResetPasswordTokenExpiry()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expiré.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        return new MessageResponse("Mot de passe mis à jour avec succès.");
    }
}
