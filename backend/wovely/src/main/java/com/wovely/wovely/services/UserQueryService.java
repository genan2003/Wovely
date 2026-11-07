package com.wovely.wovely.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wovely.wovely.models.EAccountStatus;
import com.wovely.wovely.models.User;

/** Admin CRM user search computed in MongoDB (PLAN.md 3.1). */
@Service
public class UserQueryService {

    /** Hard cap so a blank search can't dump an unbounded collection. */
    static final int MAX_RESULTS = 500;

    private final MongoTemplate mongo;

    public UserQueryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Case-insensitive substring match on username, email or full name (user input is regex-quoted),
     * optionally restricted to one account status. Both filters are optional.
     */
    /** Matches {@link User#effectiveStatus}: expired suspensions count as ACTIVE. */
    static Criteria effectiveStatusIs(EAccountStatus status, java.util.Date now) {
        Criteria expired = new Criteria().andOperator(where("accountStatus").is(EAccountStatus.SUSPENDED),
                where("suspendedUntil").lte(now));
        return switch (status) {
            case ACTIVE -> new Criteria().orOperator(where("accountStatus").is(EAccountStatus.ACTIVE), expired);
            case SUSPENDED -> new Criteria().andOperator(where("accountStatus").is(EAccountStatus.SUSPENDED),
                    new Criteria().orOperator(where("suspendedUntil").is(null), where("suspendedUntil").gt(now)));
            default -> where("accountStatus").is(status);
        };
    }

    public List<User> search(String query, EAccountStatus status) {
        List<Criteria> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    where("username").regex(p), where("email").regex(p), where("fullName").regex(p)));
        }
        if (status != null) {
            filters.add(effectiveStatusIs(status, new java.util.Date()));
        }
        Query q = filters.isEmpty() ? new Query() : new Query(new Criteria().andOperator(filters));
        q.with(Sort.by("username")).limit(MAX_RESULTS);
        return mongo.find(q, User.class);
    }
}
