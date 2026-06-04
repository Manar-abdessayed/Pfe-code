package com.example.Pfebackend.controller;

import com.example.Pfebackend.model.Recommendation;
import com.example.Pfebackend.service.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    /**
     * GET /api/recommendations?userId=xxx&filter=all|achat|vente|conserver
     * Returns active recommendations for the given user, sorted by confidence DESC.
     */
    @GetMapping
    public ResponseEntity<List<Recommendation>> getRecommendations(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "all") String filter) {
        List<Recommendation> recs = recommendationService.getActive(userId, filter);
        return ResponseEntity.ok(recs);
    }

    /**
     * POST /api/recommendations/generate
     * Regenerates recommendations from SQL Server data + optional n8n enrichment.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate() {
        List<Recommendation> recs = recommendationService.generate();
        return ResponseEntity.ok(Map.of(
            "message", "Recommandations générées avec succès",
            "count", recs.size()
        ));
    }

    /**
     * POST /api/recommendations/save-batch?userId=xxx
     * Saves a list of recommendations for the given user.
     * Replaces all current active recommendations for that user.
     */
    @PostMapping("/save-batch")
    public ResponseEntity<Map<String, Object>> saveBatch(
            @RequestParam(required = false) String userId,
            @RequestBody List<Recommendation> recs) {
        List<Recommendation> saved = recommendationService.saveBatch(userId, recs);
        return ResponseEntity.ok(Map.of(
            "message", "Recommandations sauvegardées",
            "count", saved.size()
        ));
    }
}
