package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.AssistantMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantMessageRepository extends MongoRepository<AssistantMessage, String> {
    List<AssistantMessage> findByUserIdOrderByCreatedAtAsc(String userId);
    void deleteByUserId(String userId);
}
