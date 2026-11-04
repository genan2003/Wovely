package com.wovely.wovely.services;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.wovely.wovely.clients.CatalogClient;
import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.models.EReportStatus;
import com.wovely.wovely.models.EReportTarget;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.payload.request.CreateReportRequest;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.repository.ReportRepository;
import com.wovely.wovely.repository.UserRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;

/**
 * Content reports and the "chat logs only if reported" rule (PLAN.md 3.2, README admin story).
 * A chat report must name a real conversation the reporter takes part in; admins can then read exactly
 * that conversation and every read is audited.
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final ChatMessageRepository chats;
    private final UserRepository users;
    private final CatalogClient catalog;
    private final AuditService audit;

    public ReportService(ReportRepository reports, ChatMessageRepository chats, UserRepository users,
            CatalogClient catalog, AuditService audit) {
        this.reports = reports;
        this.chats = chats;
        this.users = users;
        this.catalog = catalog;
        this.audit = audit;
    }

    public Report create(CreateReportRequest req, UserDetailsImpl me) {
        Report report = new Report();
        report.setReporterId(me.getId());
        report.setReporterName(me.getUsername());
        report.setTargetType(req.targetType().name());
        report.setReason(req.reason().trim());
        report.setStatus(EReportStatus.PENDING.name());
        report.setCreatedAt(LocalDateTime.now());

        if (req.targetType() == EReportTarget.CHAT) {
            String other = otherParticipant(req.targetId(), me.getId());
            var otherUser = users.findById(other).orElseThrow(() -> ApiException.notFound("User not found"));
            if (!chats.conversationExists(me.getId(), other)) {
                throw ApiException.badRequest("There is no conversation with this user to report");
            }
            List<String> participants = Stream.of(me.getId(), other).sorted().toList();
            report.setParticipantIds(participants);
            report.setTargetId(String.join(":", participants));
            report.setTargetName("Chat with " + otherUser.getUsername());
        } else {
            CatalogClient.ProductSnapshot product = catalog.getProduct(req.targetId());
            report.setTargetId(product.id() != null ? product.id() : req.targetId());
            report.setTargetName(product.name());
        }

        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(me.getId(), report.getTargetType(),
                report.getTargetId(), EReportStatus.PENDING.name())) {
            throw ApiException.conflict("You already have an open report for this");
        }
        return reports.save(report);
    }

    public List<Report> all() {
        return reports.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Report decide(String id, String status) {
        EReportStatus target = parseStatus(status);
        if (target == EReportStatus.PENDING) {
            throw ApiException.badRequest("status must be RESOLVED or DISMISSED");
        }
        Report report = reports.findById(id).orElseThrow(() -> ApiException.notFound("Report not found"));
        if (!EReportStatus.PENDING.name().equals(report.getStatus())) {
            throw ApiException.conflict("Report is already " + report.getStatus());
        }
        report.setStatus(target.name());
        Report saved = reports.save(report);
        audit.record("REPORT_" + target.name(), id, report.getTargetType() + " " + report.getTargetId());
        return saved;
    }

    /** The reported conversation, oldest message first. Only for CHAT reports; each read is audited. */
    public List<ChatMessage> chatLog(String reportId) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report not found"));
        if (!EReportTarget.CHAT.name().equals(report.getTargetType())) {
            throw ApiException.badRequest("Only chat reports have a chat log");
        }
        List<String> participants = report.getParticipantIds();
        if (participants == null || participants.size() != 2) {
            // reports created before participantIds existed
            participants = report.getTargetId() == null ? List.of() : Arrays.asList(report.getTargetId().split(":"));
        }
        if (participants.size() != 2) {
            throw ApiException.conflict("Report does not identify a conversation");
        }
        List<ChatMessage> log = chats.findConversationLogs(participants.get(0), participants.get(1)).stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        audit.record("CHAT_LOG_VIEWED", reportId, "conversation " + String.join(":", participants));
        return log;
    }

    /** Accepts the other user's id, or the legacy {@code "a:b"} form that must contain the reporter. */
    static String otherParticipant(String targetId, String me) {
        String[] parts = targetId.trim().split(":");
        if (parts.length == 1) {
            if (parts[0].equals(me)) {
                throw ApiException.badRequest("You cannot report a conversation with yourself");
            }
            return parts[0];
        }
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank() || parts[0].equals(parts[1])) {
            throw ApiException.badRequest("targetId must identify one conversation");
        }
        if (parts[0].equals(me)) {
            return parts[1];
        }
        if (parts[1].equals(me)) {
            return parts[0];
        }
        throw ApiException.forbidden("You can only report your own conversations");
    }

    private static EReportStatus parseStatus(String status) {
        try {
            return EReportStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("status must be RESOLVED or DISMISSED");
        }
    }
}
