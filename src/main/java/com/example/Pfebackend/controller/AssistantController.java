package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.AssistantMessage;
import com.example.Pfebackend.model.Conversation;
import com.example.Pfebackend.repository.AssistantMessageRepository;
import com.example.Pfebackend.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired private AssistantMessageRepository messageRepository;
    @Autowired private ConversationRepository     conversationRepository;

    @Value("${n8n.webhook.url:http://localhost:5678/webhook/investia-chat}")
    private String n8nWebhookUrl;

    // ═══════════════════════════════════════════════════════════════════════════
    //  CONVERSATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** List all conversations for a user, newest first.
     *  On first call, orphaned legacy messages are migrated into one conversation. */
    @GetMapping("/{userId}/conversations")
    public ResponseEntity<List<Conversation>> listConversations(@PathVariable String userId) {
        List<Conversation> convos = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId);

        if (convos.isEmpty()) {
            // Migrate any pre-existing messages that have no conversationId
            List<AssistantMessage> orphaned = messageRepository.findByUserIdAndConversationIdIsNull(userId);
            if (!orphaned.isEmpty()) {
                Conversation legacy = new Conversation(userId, "Conversation principale");
                legacy = conversationRepository.save(legacy);
                for (AssistantMessage m : orphaned) {
                    m.setConversationId(legacy.getId());
                    messageRepository.save(m);
                }
                // Set lastMessageAt to the last orphaned message's createdAt
                String last = orphaned.get(orphaned.size() - 1).getCreatedAt();
                legacy.setLastMessageAt(last);
                conversationRepository.save(legacy);
                convos = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId);
            }
        }

        return ResponseEntity.ok(convos);
    }

    /** Create a new conversation (optionally with a title). */
    @PostMapping("/{userId}/conversations")
    public ResponseEntity<Conversation> createConversation(
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body) {

        String title = (body != null && body.containsKey("title") && !body.get("title").isBlank())
                ? body.get("title")
                : "Nouvelle conversation";

        Conversation conv = conversationRepository.save(new Conversation(userId, title));
        return ResponseEntity.ok(conv);
    }

    /** Rename a conversation. */
    @PutMapping("/{userId}/conversations/{conversationId}")
    public ResponseEntity<Conversation> renameConversation(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body) {

        Optional<Conversation> opt = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Conversation conv = opt.get();
        String newTitle = body.getOrDefault("title", conv.getTitle()).trim();
        if (!newTitle.isEmpty()) conv.setTitle(newTitle);
        conversationRepository.save(conv);
        return ResponseEntity.ok(conv);
    }

    /** Delete a conversation and all its messages. */
    @DeleteMapping("/{userId}/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable String userId,
            @PathVariable String conversationId) {

        Optional<Conversation> opt = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MESSAGES WITHIN A CONVERSATION
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get all messages in a specific conversation. */
    @GetMapping("/{userId}/conversations/{conversationId}/messages")
    public ResponseEntity<List<AssistantMessage>> getConversationMessages(
            @PathVariable String userId,
            @PathVariable String conversationId) {

        Optional<Conversation> opt = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));
    }

    /** Send a message inside a conversation, forward to n8n, persist response. */
    @PostMapping("/{userId}/conversations/{conversationId}/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body) {

        Optional<Conversation> opt = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Conversation conv = opt.get();

        String userInput = body.getOrDefault("message", "").trim();
        if (userInput.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message vide."));
        }
        String email = body.getOrDefault("email", "");

        // Auto-title from first real user message
        if (conv.getTitle().equals("Nouvelle conversation")) {
            String autoTitle = userInput.length() > 50
                    ? userInput.substring(0, 50) + "…"
                    : userInput;
            conv.setTitle(autoTitle);
        }

        // Persist user message
        messageRepository.save(new AssistantMessage(userId, conversationId, "user", userInput));

        // Forward to n8n — use conversationId as sessionId so n8n tracks context per conversation
        try {
            RestTemplate restTemplate = buildRestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> n8nPayload = new LinkedHashMap<>();
            n8nPayload.put("chatInput",      userInput);
            n8nPayload.put("sessionId",      conversationId);   // per-conversation context
            n8nPayload.put("userId",         userId);
            n8nPayload.put("email",          email);
            n8nPayload.put("conversationId", conversationId);

            HttpEntity<Map<String, String>> req = new HttpEntity<>(n8nPayload, headers);

            ResponseEntity<List> n8nResp = restTemplate.exchange(
                    n8nWebhookUrl, HttpMethod.POST, req, List.class);

            List<?> respBody = n8nResp.getBody();
            Map<?, ?> first  = (respBody != null && !respBody.isEmpty())
                    ? (Map<?, ?>) respBody.get(0) : null;

            String botText = extractText(first);

            // Persist bot response
            AssistantMessage botMsg = new AssistantMessage(userId, conversationId, "bot", botText);
            messageRepository.save(botMsg);

            // Update conversation metadata
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            conv.setLastMessageAt(now);
            conversationRepository.save(conv);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response",  botText);
            result.put("messageId", botMsg.getId());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("n8n error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String fallback = "Je suis temporairement indisponible. "
                    + "Vérifiez que le service n8n est démarré puis réessayez.";
            messageRepository.save(new AssistantMessage(userId, conversationId, "bot", fallback));

            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            conv.setLastMessageAt(now);
            conversationRepository.save(conv);

            return ResponseEntity.ok(Map.of("response", fallback, "error", true));
        }
    }

    /** Clear all messages in a conversation (keeps the conversation itself). */
    @DeleteMapping("/{userId}/conversations/{conversationId}/history")
    public ResponseEntity<Void> clearConversationHistory(
            @PathVariable String userId,
            @PathVariable String conversationId) {

        Optional<Conversation> opt = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        messageRepository.deleteByConversationId(conversationId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LEGACY ENDPOINTS (backward compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<AssistantMessage>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(messageRepository.findByUserIdOrderByCreatedAtAsc(userId));
    }

    @DeleteMapping("/{userId}/history")
    public ResponseEntity<Void> clearHistory(@PathVariable String userId) {
        messageRepository.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    private String extractText(Map<?, ?> body) {
        if (body == null) return "Aucune réponse reçue.";
        for (String key : List.of("output", "text", "message", "response", "content", "answer")) {
            if (body.containsKey(key) && body.get(key) != null) {
                return body.get(key).toString().trim();
            }
        }
        return body.values().stream().findFirst()
                   .map(Object::toString).orElse("Réponse non reconnue.");
    }
}
