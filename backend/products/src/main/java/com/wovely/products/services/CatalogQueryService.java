package com.wovely.products.services;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wovely.products.models.EDeliveryEmission;
import com.wovely.products.models.Product;

/**
 * Public catalog search executed in MongoDB instead of in memory (PLAN.md 2.10).
 * User input is always regex-quoted.
 */
@Service
public class CatalogQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Filter values; {@code null} means "not filtered". */
    /**
     * @param isEco            legacy alias for {@code deliveryEmission=LOW}
     * @param deliveryEmission the dirtiest acceptable level: ZERO = zero-emission only, LOW = zero or low
     */
    public record Filter(String category, String categoryPath, Boolean isEco, EDeliveryEmission deliveryEmission,
            Boolean localPickup, Boolean isHandmade, String city, String region, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice) {

        /** The effective emission ceiling, or {@code null} when not filtering. */
        EDeliveryEmission emissionCeiling() {
            EDeliveryEmission ceiling = deliveryEmission;
            if (Boolean.TRUE.equals(isEco) && (ceiling == null || ceiling == EDeliveryEmission.STANDARD)) {
                ceiling = EDeliveryEmission.LOW;
            }
            return ceiling == EDeliveryEmission.STANDARD ? null : ceiling;
        }
    }

    /** One page of results plus the total number of matches. */
    public record Page(List<Product> items, long total) {
    }

    private final MongoTemplate mongo;

    public CatalogQueryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** @param page zero-based page, or {@code null} for all results (legacy behaviour). */
    public Page search(Filter f, Integer page, Integer size) {
        Query query = new Query(criteria(f)).with(Sort.by(Sort.Direction.ASC, "name"));
        long total = mongo.count(query, Product.class);
        if (page != null) {
            int pageSize = size == null ? 24 : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            query.skip((long) Math.max(0, page) * pageSize).limit(pageSize);
        }
        return new Page(mongo.find(query, Product.class), total);
    }

    static Criteria criteria(Filter f) {
        List<Criteria> and = new ArrayList<>();
        and.add(where("status").is("APPROVED"));
        if (notBlank(f.category())) {
            and.add(where("category").is(f.category()));
        }
        if (notBlank(f.categoryPath())) {
            and.add(where("categoryPath").regex("^" + Pattern.quote(f.categoryPath())));
        }
        EDeliveryEmission ceiling = f.emissionCeiling();
        if (ceiling != null) {
            and.add(where("deliveryEmission").in(Arrays.stream(EDeliveryEmission.values())
                    .filter(e -> e.atMost(ceiling)).map(Enum::name).toList()));
        }
        if (Boolean.TRUE.equals(f.localPickup())) {
            and.add(where("localPickup").is(true));
        }
        if (Boolean.TRUE.equals(f.isHandmade())) {
            and.add(where("isHandmade").is(true));
        }
        if (notBlank(f.city())) {
            and.add(where("city").regex(Pattern.quote(f.city().trim()), "i"));
        }
        if (notBlank(f.region())) {
            and.add(where("region").regex(Pattern.quote(f.region().trim()), "i"));
        }
        if (f.minPrice() != null || f.maxPrice() != null) {
            Criteria price = where("priceCents"); // filter bounds are RON, rounded half up to cents
            if (f.minPrice() != null) price = price.gte(com.wovely.common.money.Money.legacyCents(f.minPrice()));
            if (f.maxPrice() != null) price = price.lte(com.wovely.common.money.Money.legacyCents(f.maxPrice()));
            and.add(price);
        }
        return new Criteria().andOperator(and.toArray(Criteria[]::new));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
