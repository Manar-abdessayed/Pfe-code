package com.example.Pfebackend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Document(collection = "assistant_messages")
public class AssistantMessage {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String role;      // "user" | "bot"
    private String content;
    private String createdAt; // ISO datetime

    public AssistantMessage() {}

    public AssistantMessage(String userId, String role, String content) {
        this.userId    = userId;
        this.role      = role;
        this.content   = content;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public String getId()        { return id; }
    public void   setId(String id) { this.id = id; }

    public String getUserId()          { return userId; }
    public void   setUserId(String v)  { this.userId = v; }

    public String getRole()          { return role; }
    public void   setRole(String v)  { this.role = v; }

    public String getContent()          { return content; }
    public void   setContent(String v)  { this.content = v; }

    public String getCreatedAt()          { return createdAt; }
    public void   setCreatedAt(String v)  { this.createdAt = v; }
}
