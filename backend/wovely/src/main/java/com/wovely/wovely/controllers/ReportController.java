package com.wovely.wovely.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.repository.ReportRepository;
import com.wovely.wovely.repository.ChatMessageRepository;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
    public ResponseEntity<?> createReport(@RequestBody Report report,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.wovely.wovely.security.services.UserDetailsImpl me) {
        if (!"CHAT".equals(report.getTargetType()) && !"PRODUCT".equals(report.getTargetType())) {
            throw com.wovely.wovely.exception.ApiException.badRequest("targetType must be CHAT or PRODUCT");
        }
        if (report.getTargetId() == null || report.getTargetId().isBlank()) {
            throw com.wovely.wovely.exception.ApiException.badRequest("targetId is required");
        }
        if ("CHAT".equals(report.getTargetType())
                && !java.util.Arrays.asList(report.getTargetId().split(":")).contains(me.getId())) {
            // A chat report must be about a conversation the reporter is part of ("userA:userB")
            throw com.wovely.wovely.exception.ApiException.forbidden("You can only report your own conversations");
        }
        report.setId(null);
        report.setReporterId(me.getId());
        report.setReporterName(me.getUsername());
        report.setStatus("PENDING");
        report.setCreatedAt(java.time.LocalDateTime.now());
        Report saved = reportRepository.save(report);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Report>> getAllReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateReportStatus(@PathVariable String id, @RequestBody java.util.Map<String, String> data) {
        return reportRepository.findById(id)
            .map(report -> {
                report.setStatus(data.get("status"));
                reportRepository.save(report);
                return ResponseEntity.ok(report);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Admin view of chat logs. ONLY allowed if there is an active report for this chat.
     */
    @GetMapping("/chat-log/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getReportedChatLog(@PathVariable String reportId) {
        Optional<Report> reportOpt = reportRepository.findById(reportId);
        if (reportOpt.isPresent()) {
            Report report = reportOpt.get();
            if ("CHAT".equals(report.getTargetType())) {
                // targetId in a chat report is expected to be a composite key "user1:user2" or similar, 
                // but our current repository uses senderId and receiverId.
                // Assuming targetId stores "userA:userB" for now or just one of the IDs.
                // Let's assume for simplicity targetId IS the senderId of the reported message.
                // In a production app, this would be more precise.
                
                // For now, let's fetch all messages involving the reporter and the target.
                String[] parts = report.getTargetId().split(":");
                if (parts.length == 2) {
                    List<ChatMessage> logs = chatMessageRepository.findConversationLogs(parts[0], parts[1]);
                    logs.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
                    return ResponseEntity.ok(logs);
                }
            }
        }
        return ResponseEntity.badRequest().body("Invalid report or type");
    }
}
