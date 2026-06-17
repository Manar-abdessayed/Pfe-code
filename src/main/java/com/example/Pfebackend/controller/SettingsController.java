package com.example.Pfebackend.controller;

import com.example.Pfebackend.dto.common.MessageResponse;
import com.example.Pfebackend.dto.settings.*;
import com.example.Pfebackend.model.NotificationPrefs;
import com.example.Pfebackend.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<SettingsResponse> getSettings(@PathVariable String userId) {
        return ResponseEntity.ok(settingsService.getSettings(userId));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<SettingsResponse> updateProfile(
            @PathVariable String userId,
            @RequestBody UpdateSettingsProfileRequest request) {
        return ResponseEntity.ok(settingsService.updateProfile(userId, request));
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<MessageResponse> changePassword(
            @PathVariable String userId,
            @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(settingsService.changePassword(userId, request));
    }

    @PutMapping("/{userId}/notifications")
    public ResponseEntity<NotificationPrefs> updateNotifications(
            @PathVariable String userId,
            @RequestBody UpdateNotificationsRequest request) {
        return ResponseEntity.ok(settingsService.updateNotifications(userId, request));
    }
}
