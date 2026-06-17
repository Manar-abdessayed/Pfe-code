package com.example.Pfebackend.dto.settings;

public record UpdateSettingsProfileRequest(
        String firstName,
        String lastName,
        String email,
        String phoneNumber
) {}
