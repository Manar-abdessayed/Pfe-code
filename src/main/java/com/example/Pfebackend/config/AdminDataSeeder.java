package com.example.Pfebackend.config;

import com.example.Pfebackend.model.Admin;
import com.example.Pfebackend.repository.AdminRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataSeeder implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminDataSeeder(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.count() == 0) {
            Admin admin = new Admin();
            admin.setFirstName("Admin");
            admin.setLastName("InvestAI");
            admin.setEmail("admin@investai.com");
            admin.setPassword(passwordEncoder.encode("Admin@2024"));
            adminRepository.save(admin);
            System.out.println("✅ Compte admin par défaut créé : admin@investai.com / Admin@2024");
        }
    }
}
