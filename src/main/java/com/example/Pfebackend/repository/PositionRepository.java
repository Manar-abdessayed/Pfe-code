package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.Position;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends MongoRepository<Position, String> {
    List<Position> findByUserId(String userId);
    Optional<Position> findByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
    void deleteByUserId(String userId);
}
