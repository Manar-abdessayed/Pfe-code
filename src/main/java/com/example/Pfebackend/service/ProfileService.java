package com.example.Pfebackend.service;

import com.example.Pfebackend.dto.profile.ProfileResponse;
import com.example.Pfebackend.dto.profile.UpdateProfileRequest;
import com.example.Pfebackend.model.User;
import com.example.Pfebackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ProfileResponse getProfile(String userId) {
        User user = findUserOrThrow(userId);
        return toResponse(user);
    }

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        if (request.riskLevel() != null) {
            user.setRiskLevel(request.riskLevel());
            user.setRiskTolerance(computeRiskLabel(request.riskLevel()));
        }
        if (request.investmentGoal() != null)    user.setInvestmentGoal(request.investmentGoal());
        if (request.investmentHorizon() != null) user.setInvestmentHorizon(request.investmentHorizon());
        if (request.availableCapital() != null)  user.setAvailableCapital(request.availableCapital());
        if (request.sectors() != null)           user.setSectors(request.sectors());

        return toResponse(userRepository.save(user));
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé."));
    }

    private ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRiskLevel(), u.getRiskTolerance(), u.getInvestmentGoal(),
                u.getInvestmentHorizon(), u.getAvailableCapital(), u.getSectors());
    }

    private String computeRiskLabel(int level) {
        if (level <= 3) return "Prudent";
        if (level <= 6) return "Modéré";
        return "Agressif";
    }
}
