package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.AssistantMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantMessageRepository extends MongoRepository<AssistantMessage, String> {

    // Legacy: all messages for a user (no conversation filtering)
    List<AssistantMessage> findByUserIdOrderByCreatedAtAsc(String userId);
    void deleteByUserId(String userId);

    // Per-conversation queries
    List<AssistantMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    void deleteByConversationId(String conversationId);

    // Orphaned messages (created before multi-conversation support)
    List<AssistantMessage> findByUserIdAndConversationIdIsNull(String userId);
}
