package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.repository.ReportRepository;
import com.wovely.wovely.support.AbstractMongoIT;

/** PLAN.md 3.2 — repository queries behind report validation (real Mongo). */
class ReportQueriesIT extends AbstractMongoIT {

    @Autowired ChatMessageRepository chats;
    @Autowired ReportRepository reports;

    @Test
    void conversationExistsInEitherDirectionOnly() {
        String a = "a-" + UUID.randomUUID(), b = "b-" + UUID.randomUUID(), c = "c-" + UUID.randomUUID();
        chats.save(new ChatMessage(b, a, null, null, "hello"));

        assertThat(chats.conversationExists(a, b)).isTrue();
        assertThat(chats.conversationExists(b, a)).isTrue();
        assertThat(chats.conversationExists(a, c)).isFalse();
    }

    @Test
    void openReportLookupIgnoresDecidedReports() {
        String me = "r-" + UUID.randomUUID();
        Report decided = new Report(me, "PRODUCT", "p-1", "x");
        decided.setStatus("RESOLVED");
        reports.save(decided);
        assertThat(reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(me, "PRODUCT", "p-1", "PENDING")).isFalse();
        reports.save(new Report(me, "PRODUCT", "p-1", "again"));
        assertThat(reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(me, "PRODUCT", "p-1", "PENDING")).isTrue();
    }
}
