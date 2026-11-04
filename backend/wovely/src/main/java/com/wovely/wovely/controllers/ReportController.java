package com.wovely.wovely.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.payload.request.CreateReportRequest;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.services.ReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<Report> createReport(@Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal UserDetailsImpl me) {
        return ResponseEntity.ok(reportService.create(request, me));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Report> getAllReports() {
        return reportService.all();
    }

    /** {@code {"status": "RESOLVED" | "DISMISSED"}}; only pending reports can be decided. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Report updateReportStatus(@PathVariable String id, @RequestBody Map<String, String> data) {
        return reportService.decide(id, data.get("status"));
    }

    /** Admin view of a conversation — only through a CHAT report about it (README admin story). */
    @GetMapping("/chat-log/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ChatMessage> getReportedChatLog(@PathVariable String reportId) {
        return reportService.chatLog(reportId);
    }
}
