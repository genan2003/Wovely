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
    public List<User> search(String query, EAccountStatus status) {
        List<Criteria> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    where("username").regex(p), where("email").regex(p), where("fullName").regex(p)));
        }
        if (status != null) {
            filters.add(where("accountStatus").is(status));
        }
        Query q = filters.isEmpty() ? new Query() : new Query(new Criteria().andOperator(filters));
        q.with(Sort.by("username")).limit(MAX_RESULTS);
        return mongo.find(q, User.class);
    }
}
