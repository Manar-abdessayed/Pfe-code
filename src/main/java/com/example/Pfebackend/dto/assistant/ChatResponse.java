package com.example.Pfebackend.dto.assistant;

public record ChatResponse(String response, String messageId, String title, Boolean error) {}
