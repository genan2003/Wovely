package com.wovely.wovely.services;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.List;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.wovely.wovely.models.ChatMessage;

/** Chat read models computed in MongoDB (PLAN.md 2.10). */
@Service
public class ChatQueryService {

    private final MongoTemplate mongo;

    public ChatQueryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Latest message per conversation partner of {@code userId}, newest conversation first. */
    public List<ChatMessage> latestPerConversation(String userId) {
        AggregationOperation groupByPartner = ctx -> new Document("$group", new Document()
                .append("_id", new Document("$cond", List.of(
                        new Document("$eq", List.of("$senderId", userId)), "$receiverId", "$senderId")))
                .append("msg", new Document("$first", "$$ROOT")));
        AggregationOperation unwrap = ctx -> new Document("$replaceRoot", new Document("newRoot", "$msg"));

        var aggregation = newAggregation(
                match(new Criteria().orOperator(where("senderId").is(userId), where("receiverId").is(userId))),
                sort(Sort.Direction.DESC, "timestamp"),
                groupByPartner,
                unwrap,
                sort(Sort.Direction.DESC, "timestamp"));
        return mongo.aggregate(aggregation, ChatMessage.class, ChatMessage.class).getMappedResults();
    }
}
