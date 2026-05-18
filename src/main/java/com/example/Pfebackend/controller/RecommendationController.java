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
     * GET /api/recommendations?filter=all|achat|vente|conserver
     * Returns active recommendations sorted by confidence DESC.
     */
    @GetMapping
    public ResponseEntity<List<Recommendation>> getRecommendations(
            @RequestParam(defaultValue = "all") String filter) {
        List<Recommendation> recs = recommendationService.getActive(filter);
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
     * POST /api/recommendations/save-batch
     * Saves a list of recommendations parsed by the frontend (n8n agent flow).
     * Replaces all current active recommendations.
     */
    @PostMapping("/save-batch")
    public ResponseEntity<Map<String, Object>> saveBatch(@RequestBody List<Recommendation> recs) {
        List<Recommendation> saved = recommendationService.saveBatch(recs);
        return ResponseEntity.ok(Map.of(
            "message", "Recommandations sauvegardées",
            "count", saved.size()
        ));
    }
}
