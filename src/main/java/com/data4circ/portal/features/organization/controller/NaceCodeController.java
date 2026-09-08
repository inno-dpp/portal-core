package com.data4circ.portal.features.organization.controller;

import com.data4circ.portal.features.organization.entity.NaceCode;
import com.data4circ.portal.features.organization.service.NaceCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nace-codes")
public class NaceCodeController {

    @Autowired
    private NaceCodeService naceCodeService;

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, String>>> search(@RequestParam String q) {
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        List<NaceCode> results = naceCodeService.search(q);
        List<Map<String, String>> response = results.stream()
                .limit(50)
                .map(nc -> Map.of(
                        "code", nc.getCode(),
                        "description", nc.getDescription(),
                        "section", nc.getSection() != null ? nc.getSection() : "",
                        "sectionLabel", nc.getSectionLabel() != null ? nc.getSectionLabel() : "",
                        "label", nc.getDisplayLabel()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
