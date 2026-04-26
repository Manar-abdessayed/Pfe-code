package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.AssistantMessage;
import com.example.Pfebackend.repository.AssistantMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired
    private AssistantMessageRepository messageRepository;

    @Value("${n8n.webhook.url:http://localhost:5678/webhook/invest-ia-chat}")
    private String n8nWebhookUrl;

    // ─── GET /api/assistant/{userId}/history ────────────────────────────────────
    @GetMapping("/{userId}/history")
    public ResponseEntity<List<AssistantMessage>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(messageRepository.findByUserIdOrderByCreatedAtAsc(userId));
    }

    // ─── POST /api/assistant/{userId}/chat ──────────────────────────────────────
    @PostMapping("/{userId}/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        String userInput = body.getOrDefault("message", "").trim();
        if (userInput.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message vide."));
        }

        // Persist user message
        messageRepository.save(new AssistantMessage(userId, "user", userInput));

        // Forward to n8n agent
        try {
            RestTemplate restTemplate = buildRestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> n8nPayload = new LinkedHashMap<>();
            n8nPayload.put("chatInput", userInput);
            n8nPayload.put("sessionId", userId);

            HttpEntity<Map<String, String>> req = new HttpEntity<>(n8nPayload, headers);
            ResponseEntity<Map> n8nResp = restTemplate.exchange(
                    n8nWebhookUrl, HttpMethod.POST, req, Map.class);

            String botText = extractText(n8nResp.getBody());

            // Persist bot response
            AssistantMessage botMsg = new AssistantMessage(userId, "bot", botText);
            messageRepository.save(botMsg);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response",  botText);
            result.put("messageId", botMsg.getId());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            String fallback = "Je suis temporairement indisponible. "
                    + "Vérifiez que le service n8n est démarré puis réessayez.";
            messageRepository.save(new AssistantMessage(userId, "bot", fallback));
            return ResponseEntity.ok(Map.of("response", fallback, "error", true));
        }
    }

    // ─── DELETE /api/assistant/{userId}/history ──────────────────────────────────
    @DeleteMapping("/{userId}/history")
    public ResponseEntity<Void> clearHistory(@PathVariable String userId) {
        messageRepository.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);   // 10 s connect
        factory.setReadTimeout(120_000);     // 2 min for AI
        return new RestTemplate(factory);
    }

    private String extractText(Map<?, ?> body) {
        if (body == null) return "Aucune réponse reçue.";
        for (String key : List.of("output", "text", "message", "response", "content", "answer")) {
            if (body.containsKey(key) && body.get(key) != null) {
                return body.get(key).toString().trim();
            }
        }
        return body.values().stream().findFirst().map(Object::toString).orElse("Réponse non reconnue.");
    }
}
