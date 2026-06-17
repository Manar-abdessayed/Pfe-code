package com.example.Pfebackend.dto.auth;

public record ResetPasswordRequest(String token, String newPassword) {}
