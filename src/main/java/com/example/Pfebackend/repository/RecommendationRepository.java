package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    List<Recommendation> findByActiveOrderByConfidenceDesc(boolean active);

    List<Recommendation> findByActionAndActiveOrderByConfidenceDesc(String action, boolean active);

    void deleteByActive(boolean active);
}
