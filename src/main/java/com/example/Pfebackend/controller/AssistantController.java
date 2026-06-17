package com.example.Pfebackend.controller;

import com.example.Pfebackend.dto.assistant.*;
import com.example.Pfebackend.model.AssistantMessage;
import com.example.Pfebackend.model.Conversation;
import com.example.Pfebackend.service.AssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/{userId}/conversations")
    public ResponseEntity<List<Conversation>> listConversations(@PathVariable String userId) {
        return ResponseEntity.ok(assistantService.listConversations(userId));
    }

    @PostMapping("/{userId}/conversations")
    public ResponseEntity<Conversation> createConversation(
            @PathVariable String userId,
            @RequestBody(required = false) CreateConversationRequest request) {
        String title = request != null ? request.title() : null;
        return ResponseEntity.ok(assistantService.createConversation(userId, title));
    }

    @PutMapping("/{userId}/conversations/{conversationId}")
    public ResponseEntity<Conversation> renameConversation(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody RenameConversationRequest request) {
        return ResponseEntity.ok(assistantService.renameConversation(userId, conversationId, request.title()));
    }

    @DeleteMapping("/{userId}/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable String userId,
            @PathVariable String conversationId) {
        assistantService.deleteConversation(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/conversations/{conversationId}/messages")
    public ResponseEntity<List<AssistantMessage>> getConversationMessages(
            @PathVariable String userId,
            @PathVariable String conversationId) {
        return ResponseEntity.ok(assistantService.getConversationMessages(userId, conversationId));
    }

    @PostMapping("/{userId}/conversations/{conversationId}/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody ChatRequest request) {
        return ResponseEntity.ok(assistantService.chat(userId, conversationId, request));
    }

    @DeleteMapping("/{userId}/conversations/{conversationId}/history")
    public ResponseEntity<Void> clearConversationHistory(
            @PathVariable String userId,
            @PathVariable String conversationId) {
        assistantService.clearConversationHistory(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<AssistantMessage>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(assistantService.getLegacyHistory(userId));
    }

    @DeleteMapping("/{userId}/history")
    public ResponseEntity<Void> clearHistory(@PathVariable String userId) {
        assistantService.clearLegacyHistory(userId);
        return ResponseEntity.noContent().build();
    }
}
