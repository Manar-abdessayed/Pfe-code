package com.example.Pfebackend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String phoneNumber;
    private String role = "USER";

    // Profil investisseur
    private int riskLevel = 5;               // 1-10 (Prudent → Agressif)
    private String riskTolerance;            // label: Prudent / Modéré / Agressif
    private String investmentGoal;           // CROISSANCE / REVENUS / PRESERVATION
    private String investmentHorizon;        // COURT / MOYEN / LONG
    private double availableCapital = 0;
    private List<String> sectors;

    private NotificationPrefs notificationPrefs = new NotificationPrefs();

    // Constructeurs
    public User() {}

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getRiskLevel() { return riskLevel; }
    public void setRiskLevel(int riskLevel) { this.riskLevel = riskLevel; }

    public String getRiskTolerance() { return riskTolerance; }
    public void setRiskTolerance(String riskTolerance) { this.riskTolerance = riskTolerance; }

    public String getInvestmentGoal() { return investmentGoal; }
    public void setInvestmentGoal(String investmentGoal) { this.investmentGoal = investmentGoal; }

    public String getInvestmentHorizon() { return investmentHorizon; }
    public void setInvestmentHorizon(String investmentHorizon) { this.investmentHorizon = investmentHorizon; }

    public double getAvailableCapital() { return availableCapital; }
    public void setAvailableCapital(double availableCapital) { this.availableCapital = availableCapital; }

    public List<String> getSectors() { return sectors; }
    public void setSectors(List<String> sectors) { this.sectors = sectors; }

    public NotificationPrefs getNotificationPrefs() { return notificationPrefs; }
    public void setNotificationPrefs(NotificationPrefs notificationPrefs) { this.notificationPrefs = notificationPrefs; }
}
