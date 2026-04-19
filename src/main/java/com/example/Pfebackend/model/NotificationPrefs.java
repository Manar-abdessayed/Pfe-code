package com.example.Pfebackend.model;

public class NotificationPrefs {

    private boolean emailNotifications = true;
    private boolean marketAlerts = true;
    private boolean portfolioAlerts = true;

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public boolean isMarketAlerts() { return marketAlerts; }
    public void setMarketAlerts(boolean marketAlerts) { this.marketAlerts = marketAlerts; }

    public boolean isPortfolioAlerts() { return portfolioAlerts; }
    public void setPortfolioAlerts(boolean portfolioAlerts) { this.portfolioAlerts = portfolioAlerts; }
}
