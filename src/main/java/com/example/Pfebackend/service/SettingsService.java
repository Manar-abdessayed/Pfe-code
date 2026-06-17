package com.example.Pfebackend.service;

import com.example.Pfebackend.dto.common.MessageResponse;
import com.example.Pfebackend.dto.settings.*;
import com.example.Pfebackend.model.NotificationPrefs;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SettingsService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public SettingsResponse getSettings(String userId) {
        return toResponse(findUserOrThrow(userId));
    }

    public SettingsResponse updateProfile(String userId, UpdateSettingsProfileRequest request) {
        User user = findUserOrThrow(userId);

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé.");
        }

        if (request.firstName() != null)   user.setFirstName(request.firstName());
        if (request.lastName() != null)    user.setLastName(request.lastName());
        if (request.email() != null)       user.setEmail(request.email());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());

        return toResponse(userRepository.save(user));
    }

    public MessageResponse changePassword(String userId, ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mot de passe actuel incorrect.");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit contenir au moins 6 caractères.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return new MessageResponse("Mot de passe modifié avec succès.");
    }

    public NotificationPrefs updateNotifications(String userId, UpdateNotificationsRequest request) {
        User user = findUserOrThrow(userId);
        NotificationPrefs prefs = user.getNotificationPrefs() != null
                ? user.getNotificationPrefs() : new NotificationPrefs();

        if (request.emailNotifications() != null) prefs.setEmailNotifications(request.emailNotifications());
        if (request.marketAlerts() != null)        prefs.setMarketAlerts(request.marketAlerts());
        if (request.portfolioAlerts() != null)     prefs.setPortfolioAlerts(request.portfolioAlerts());

        user.setNotificationPrefs(prefs);
        userRepository.save(user);
        return prefs;
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé."));
    }

    private SettingsResponse toResponse(User u) {
        NotificationPrefs p = u.getNotificationPrefs() != null
                ? u.getNotificationPrefs() : new NotificationPrefs();
        return new SettingsResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole(), p.isEmailNotifications(), p.isMarketAlerts(), p.isPortfolioAlerts());
    }
}
