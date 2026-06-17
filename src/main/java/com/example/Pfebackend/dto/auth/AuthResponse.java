package com.example.Pfebackend.dto.auth;

public record AuthResponse(
        String token,
        String id,
        String email,
        String firstName,
        String lastName,
        String role,
        String riskTolerance
) {}
