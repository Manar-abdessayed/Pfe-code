package com.example.Pfebackend.controller;

import com.example.Pfebackend.dto.admin.AdminStatsResponse;
import com.example.Pfebackend.dto.admin.AdminUserDetail;
import com.example.Pfebackend.dto.admin.AdminUserSummary;
import com.example.Pfebackend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserSummary>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    @GetMapping("/users/{id}/details")
    public ResponseEntity<AdminUserDetail> getUserDetails(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts() {
        return ResponseEntity.ok(adminService.getAlerts());
    }

    @GetMapping("/active-users")
    public ResponseEntity<List<Map<String, Object>>> getActiveUsers() {
        return ResponseEntity.ok(adminService.getActiveUsers());
    }

    @GetMapping("/registration-trend")
    public ResponseEntity<List<Map<String, Object>>> getRegistrationTrend() {
        return ResponseEntity.ok(adminService.getRegistrationTrend());
    }

    @GetMapping("/risk-distribution")
    public ResponseEntity<List<Map<String, Object>>> getRiskDistribution() {
        return ResponseEntity.ok(adminService.getRiskDistribution());
    }

    @GetMapping("/system-services")
    public ResponseEntity<List<Map<String, Object>>> getSystemServices() {
        return ResponseEntity.ok(adminService.getSystemServices());
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "marketDataRefreshInterval", 30,
            "sessionTimeoutMinutes",     60,
            "maxLoginAttempts",          5,
            "emailNotificationsEnabled", true,
            "smtpHost",                  "smtp.gmail.com",
            "smtpPort",                  587,
            "smtpUser",                  "",
            "defaultRiskLevel",          5,
            "maintenanceMode",           false,
            "logLevel",                  "INFO"
        ));
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> config) {
        return ResponseEntity.ok(config);
    }
}
