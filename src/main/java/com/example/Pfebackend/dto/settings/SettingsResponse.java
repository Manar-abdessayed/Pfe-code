package com.example.Pfebackend.dto.settings;

public record SettingsResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String role,
        boolean emailNotifications,
        boolean marketAlerts,
        boolean portfolioAlerts
) {}
