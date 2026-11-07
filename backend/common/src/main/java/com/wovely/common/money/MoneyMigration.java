package com.wovely.common.money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * One-off, idempotent migration of amounts stored as doubles to whole cents (PLAN.md 7.5). Each document is
 * rewritten with one guarded update — "only if the legacy field is still there" — that sets the cents fields and
 * removes the legacy ones, so running it twice, or on two instances at once, changes nothing the second time and
 * never leaves both representations behind.
 */
public final class MoneyMigration {

    private static final Logger logger = LoggerFactory.getLogger(MoneyMigration.class);

    private MoneyMigration() {
    }

    /**
     * @param fields      top-level legacy field → cents field (e.g. {@code price → priceCents})
     * @param arrayField  an array of embedded documents to migrate too (e.g. {@code items}), or {@code null}
     * @param arrayFields legacy → cents field inside each array element
     * @return number of documents rewritten
     */
    public static int toCents(MongoTemplate mongo, String collection, Map<String, String> fields, String arrayField,
            Map<String, String> arrayFields) {
        List<Criteria> legacy = new ArrayList<>();
        fields.keySet().forEach(f -> legacy.add(Criteria.where(f).exists(true)));
        if (arrayField != null) {
            arrayFields.keySet().forEach(f -> legacy.add(Criteria.where(arrayField + "." + f).exists(true)));
        }
        if (legacy.isEmpty()) {
            return 0;
        }
        Query pending = new Query(new Criteria().orOperator(legacy));
        int migrated = 0;
        for (Document doc : mongo.find(pending, Document.class, collection)) {
            Update update = new Update();
            List<Criteria> guard = new ArrayList<>();
            for (Map.Entry<String, String> f : fields.entrySet()) {
                if (doc.containsKey(f.getKey())) {
                    Object value = doc.get(f.getKey());
                    if (value == null) {
                        update.unset(f.getValue());
                    } else {
                        update.set(f.getValue(), cents(value));
                    }
                    update.unset(f.getKey());
                    guard.add(Criteria.where(f.getKey()).exists(true));
                }
            }
            if (arrayField != null && doc.get(arrayField) instanceof List<?> elements) {
                List<Object> rewritten = new ArrayList<>(elements.size());
                boolean changed = false;
                for (Object element : elements) {
                    if (element instanceof Document d) {
                        Document copy = new Document(d);
                        for (Map.Entry<String, String> f : arrayFields.entrySet()) {
                            if (copy.containsKey(f.getKey())) {
                                Object value = copy.remove(f.getKey());
                                if (value != null) {
                                    copy.put(f.getValue(), cents(value));
                                }
                                changed = true;
                            }
                        }
                        rewritten.add(copy);
                    } else {
                        rewritten.add(element);
                    }
                }
                if (changed) {
                    update.set(arrayField, rewritten);
                    // the array is replaced wholesale: only if nobody rewrote it since we read it
                    guard.add(Criteria.where(arrayField).is(elements));
                }
            }
            if (guard.isEmpty()) {
                continue;
            }
            Query one = new Query(Criteria.where("_id").is(doc.get("_id")).andOperator(guard));
            if (mongo.updateFirst(one, update, collection).getModifiedCount() > 0) {
                migrated++;
            }
        }
        if (migrated > 0) {
            logger.info("Converted amounts to cents in {} {} documents", migrated, collection);
        }
        return migrated;
    }

    static long cents(Object value) {
        if (value instanceof Decimal128 d) {
            return Money.legacyCents(d.bigDecimalValue());
        }
        if (value instanceof Number n) {
            return Money.legacyCents(n);
        }
        if (value instanceof String s && !s.isBlank()) {
            return Money.legacyCents(new java.math.BigDecimal(s.trim()));
        }
        throw new IllegalStateException("Not an amount: " + value);
    }
}
