package com.example.Pfebackend.service;

import com.example.Pfebackend.dto.assistant.ChatRequest;
import com.example.Pfebackend.dto.assistant.ChatResponse;
import com.example.Pfebackend.model.AssistantMessage;
import com.example.Pfebackend.model.Conversation;
import com.example.Pfebackend.repository.AssistantMessageRepository;
import com.example.Pfebackend.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AssistantService {

    private static final String NEW_CONV_TITLE = "Nouvelle conversation";

    private static final Set<String> GREETINGS = Set.of(
        "bonjour", "bonsoir", "salut", "hello", "hi", "hey", "coucou",
        "ok", "oui", "non", "merci", "svp", "stp", "allo", "allô"
    );

    private final AssistantMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    @Value("${n8n.webhook.url:http://localhost:5678/webhook/investia-chat}")
    private String n8nWebhookUrl;

    public AssistantService(AssistantMessageRepository messageRepository,
                            ConversationRepository conversationRepository) {
        this.messageRepository     = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    public List<Conversation> listConversations(String userId) {
        List<Conversation> convos = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId);
        if (convos.isEmpty()) {
            List<AssistantMessage> orphaned = messageRepository.findByUserIdAndConversationIdIsNull(userId);
            if (!orphaned.isEmpty()) {
                Conversation legacy = conversationRepository.save(new Conversation(userId, "Conversation principale"));
                orphaned.forEach(m -> { m.setConversationId(legacy.getId()); messageRepository.save(m); });
                legacy.setLastMessageAt(orphaned.get(orphaned.size() - 1).getCreatedAt());
                conversationRepository.save(legacy);
                convos = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId);
            }
        }
        return convos;
    }

    public Conversation createConversation(String userId, String title) {
        String resolved = (title != null && !title.isBlank()) ? title : NEW_CONV_TITLE;
        return conversationRepository.save(new Conversation(userId, resolved));
    }

    public Conversation renameConversation(String userId, String conversationId, String newTitle) {
        Conversation conv = findConversationOrThrow(userId, conversationId);
        String title = newTitle != null ? newTitle.trim() : "";
        if (!title.isEmpty()) conv.setTitle(title);
        return conversationRepository.save(conv);
    }

    public void deleteConversation(String userId, String conversationId) {
        findConversationOrThrow(userId, conversationId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    public List<AssistantMessage> getConversationMessages(String userId, String conversationId) {
        findConversationOrThrow(userId, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public ChatResponse chat(String userId, String conversationId, ChatRequest request) {
        Conversation conv = findConversationOrThrow(userId, conversationId);

        String userInput = request.message() != null ? request.message().trim() : "";
        if (userInput.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message vide.");
        }

        messageRepository.save(new AssistantMessage(userId, conversationId, "user", userInput));

        try {
            RestTemplate restTemplate = buildRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("chatInput",      userInput);
            payload.put("sessionId",      conversationId);
            payload.put("userId",         userId);
            payload.put("email",          request.email() != null ? request.email() : "");
            payload.put("conversationId", conversationId);

            ResponseEntity<List<Map<String, Object>>> n8nResp = restTemplate.exchange(
                n8nWebhookUrl, HttpMethod.POST, new HttpEntity<>(payload, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {});

            List<Map<String, Object>> body = n8nResp.getBody();
            Map<String, Object> first = (body != null && !body.isEmpty()) ? body.get(0) : null;
            String botText  = extractText(first);

            if (NEW_CONV_TITLE.equals(conv.getTitle())) {
                generateTitle(userInput, botText).ifPresent(conv::setTitle);
            }

            AssistantMessage botMsg = messageRepository.save(
                new AssistantMessage(userId, conversationId, "bot", botText));

            conv.setLastMessageAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            conversationRepository.save(conv);

            return new ChatResponse(botText, botMsg.getId(),
                NEW_CONV_TITLE.equals(conv.getTitle()) ? null : conv.getTitle(), null);

        } catch (Exception e) {
            String fallback = "Je suis temporairement indisponible. " +
                "Vérifiez que le service n8n est démarré puis réessayez.";

            if (NEW_CONV_TITLE.equals(conv.getTitle()) && !isGreeting(userInput)) {
                conv.setTitle(userInput.length() > 55 ? userInput.substring(0, 55) + "…" : userInput);
            }

            messageRepository.save(new AssistantMessage(userId, conversationId, "bot", fallback));
            conv.setLastMessageAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            conversationRepository.save(conv);

            return new ChatResponse(fallback, null,
                NEW_CONV_TITLE.equals(conv.getTitle()) ? null : conv.getTitle(), true);
        }
    }

    public void clearConversationHistory(String userId, String conversationId) {
        findConversationOrThrow(userId, conversationId);
        messageRepository.deleteByConversationId(conversationId);
    }

    public List<AssistantMessage> getLegacyHistory(String userId) {
        return messageRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public void clearLegacyHistory(String userId) {
        messageRepository.deleteByUserId(userId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Conversation findConversationOrThrow(String userId, String conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation non trouvée."));
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    private boolean isGreeting(String message) {
        return GREETINGS.contains(message.toLowerCase().trim());
    }

    private Optional<String> generateTitle(String userMessage, String botResponse) {
        if (isGreeting(userMessage)) return Optional.empty();
        if (userMessage.length() > 20) {
            return Optional.of(userMessage.length() > 55 ? userMessage.substring(0, 55) + "…" : userMessage);
        }
        String clean = botResponse.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("\\*(.*?)\\*", "$1").replaceAll("(?m)^#+\\s*", "").trim();
        for (String part : clean.split("[.!\\n]")) {
            String t = part.trim();
            if (t.length() > 20) return Optional.of(t.length() > 55 ? t.substring(0, 55) + "…" : t);
        }
        return Optional.of(userMessage.length() > 55 ? userMessage.substring(0, 55) + "…" : userMessage);
    }

    private String extractText(Map<String, Object> body) {
        if (body == null) return "Aucune réponse reçue.";
        for (String key : List.of("output", "text", "message", "response", "content", "answer")) {
            if (body.containsKey(key) && body.get(key) != null) return body.get(key).toString().trim();
        }
        return body.values().stream().findFirst().map(Object::toString).orElse("Réponse non reconnue.");
    }
}
