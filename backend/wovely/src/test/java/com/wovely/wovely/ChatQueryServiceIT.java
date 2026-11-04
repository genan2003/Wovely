package com.wovely.wovely;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.wovely.models.ChatMessage;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.services.ChatQueryService;
import com.wovely.wovely.support.AbstractMongoIT;

/** PLAN.md 2.10 — conversation list via MongoDB aggregation (real Mongo). */
class ChatQueryServiceIT extends AbstractMongoIT {

    @Autowired ChatQueryService chatQueryService;
    @Autowired ChatMessageRepository repository;

    private ChatMessage msg(String from, String to, String content, int minutesAgo) {
        ChatMessage m = new ChatMessage(from, to, null, null, content);
        m.setTimestamp(LocalDateTime.now().minusMinutes(minutesAgo));
        return repository.save(m);
    }

    @Test
    void returnsLatestMessagePerPartnerNewestFirst() {
        String me = "me-" + UUID.randomUUID(), alice = "alice-" + UUID.randomUUID(), bob = "bob-" + UUID.randomUUID();
        msg(me, alice, "hi alice", 30);
        msg(alice, me, "hi back", 20);
        msg(bob, me, "old bob", 50);
        msg(me, bob, "latest bob", 5);
        msg(alice, bob, "not mine", 1);

        var result = chatQueryService.latestPerConversation(me);

        assertThat(result).extracting(ChatMessage::getContent).containsExactly("latest bob", "hi back");
    }
}
