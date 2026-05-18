package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByUserIdOrderByLastMessageAtDesc(String userId);
    Optional<Conversation> findByIdAndUserId(String id, String userId);
    void deleteByUserId(String userId);
}
