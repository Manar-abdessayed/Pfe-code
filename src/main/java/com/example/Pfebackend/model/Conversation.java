package com.example.Pfebackend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;
    private String createdAt;
    private String lastMessageAt;

    public Conversation() {}

    public Conversation(String userId, String title) {
        this.userId = userId;
        this.title  = title;
        String now  = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.createdAt       = now;
        this.lastMessageAt   = now;
    }

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }

    public String getUserId()                   { return userId; }
    public void   setUserId(String userId)      { this.userId = userId; }

    public String getTitle()                    { return title; }
    public void   setTitle(String title)        { this.title = title; }

    public String getCreatedAt()                { return createdAt; }
    public void   setCreatedAt(String v)        { this.createdAt = v; }

    public String getLastMessageAt()            { return lastMessageAt; }
    public void   setLastMessageAt(String v)    { this.lastMessageAt = v; }
}
