package com.example.Pfebackend.dto.auth;

public record RegisterRequest(
        String email,
        String password,
        String firstName,
        String lastName,
        String phoneNumber,
        String role,
        String riskTolerance,
        String investmentGoal
) {}
