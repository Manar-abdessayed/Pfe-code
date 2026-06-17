package com.example.Pfebackend.dto.settings;

public record UpdateNotificationsRequest(
        Boolean emailNotifications,
        Boolean marketAlerts,
        Boolean portfolioAlerts
) {}
