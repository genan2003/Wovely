package com.wovely.products;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.wovely.products.models.Product;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.CatalogQueryService;
import com.wovely.products.services.CatalogQueryService.Filter;
import com.wovely.products.support.AbstractMongoIT;

/** PLAN.md 2.10 / 3.5 — catalog filtering in MongoDB (real Mongo). */
class CatalogQueryServiceIT extends AbstractMongoIT {

    @Autowired CatalogQueryService catalog;
    @Autowired ProductRepository repository;

    static final String CAT = "IT-Cat-" + System.nanoTime();

    private void save(String name, String status, double price, String co2, String city, boolean handmade) {
        Product p = new Product(name, "", Math.round(price * 100), "s", "", CAT, CAT + " > Sub", List.of(), city, "R", null, null, co2,
                "Courier", handmade, 1, 1);
        p.setStatus(status);
        repository.save(p.refreshDeliveryEmission());
    }

    @BeforeEach
    void seed() {
        if (repository.findByCategory(CAT).isEmpty()) {
            save("A basket", "APPROVED", 10, "Low", "Sibiu", true);
            save("B mug", "APPROVED", 25, "LOW", "Cluj-Napoca", false);
            save("C vase", "APPROVED", 60, "High", "Sibiu", true);
            save("D pending", "PENDING", 15, "Low", "Sibiu", true);
            save("E archived", "ARCHIVED", 15, "Low", "Sibiu", true);
        }
    }

    private List<String> names(Filter f) {
        return catalog.search(f, null, null).items().stream().map(Product::getName).toList();
    }

    @Test
    void onlyApprovedProductsAreListed() {
        assertThat(names(new Filter(CAT, null, null, null, null, null, null, null, null, null)))
            .containsExactly("A basket", "B mug", "C vase");
    }

    @Test
    void ecoFilterIsCaseInsensitiveAndPriceRangeApplies() {
        assertThat(names(new Filter(CAT, null, true, null, null, null, null, null, null, new java.math.BigDecimal("30.0")))).containsExactly("A basket", "B mug");
    }

    @Test
    void cityAndHandmadeFilters() {
        assertThat(names(new Filter(CAT, null, null, null, null, true, "sib", null, null, null))).containsExactly("A basket", "C vase");
    }

    @Test
    void userInputIsRegexQuoted() {
        assertThat(names(new Filter(CAT, null, null, null, null, null, "S.b", null, null, null))).isEmpty();
        assertThat(names(new Filter(null, "IT-Cat-.*", null, null, null, null, null, null, null, null))).isEmpty();
        assertThat(names(new Filter(null, CAT + " >", null, null, null, null, null, null, null, null))).hasSize(3);
    }

    @Test
    void paginationReturnsPageAndTotal() {
        var page = catalog.search(new Filter(CAT, null, null, null, null, null, null, null, null, null), 1, 2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(Product::getName).containsExactly("C vase");
    }

    // ---------------------------------------------------------------- 3.5 delivery emission

    static final String ECO_CAT = "IT-Eco-" + System.nanoTime();

    @Autowired com.wovely.products.services.DeliveryEmissionBackfill backfill;
    @Autowired org.springframework.data.mongodb.core.MongoTemplate mongo;

    private Product eco(String name, String shipping, String co2, Boolean pickup) {
        Product p = new Product(name, "", 500L, "s", "", ECO_CAT, ECO_CAT, List.of(), "Iasi", "R", null, null, co2,
                shipping, true, 1, 1);
        p.setStatus("APPROVED");
        p.setLocalPickup(pickup);
        return repository.save(p.refreshDeliveryEmission());
    }

    private List<String> eco(com.wovely.products.models.EDeliveryEmission level, Boolean pickup, Boolean isEco) {
        return names(new Filter(ECO_CAT, null, isEco, level, pickup, null, null, null, null, null));
    }

    @Test
    void deliveryEmissionCeilingFiltersZeroAndLow() {
        if (repository.findByCategory(ECO_CAT).isEmpty()) {
            eco("Z bike", "Bicycle courier", "Medium", null);
            eco("Z pickup", "Courier", "High", true);
            eco("L neutral", "Carbon Neutral", "Medium", false);
            eco("S express", "Express", "High", false);
        }
        var ZERO = com.wovely.products.models.EDeliveryEmission.ZERO;
        var LOW = com.wovely.products.models.EDeliveryEmission.LOW;
        var STANDARD = com.wovely.products.models.EDeliveryEmission.STANDARD;
        assertThat(eco(ZERO, null, null)).containsExactly("Z bike", "Z pickup");
        assertThat(eco(LOW, null, null)).containsExactly("L neutral", "Z bike", "Z pickup");
        assertThat(eco(null, null, true)).containsExactly("L neutral", "Z bike", "Z pickup");
        assertThat(eco(STANDARD, null, null)).hasSize(4);
        assertThat(eco(null, true, null)).containsExactly("Z pickup");
        assertThat(eco(ZERO, null, true)).containsExactly("Z bike", "Z pickup");
    }

    @Test
    void backfillDerivesMissingLevelsOnce() {
        Product legacy = new Product("Legacy", "", 100L, "s", "", "IT-Legacy", "IT-Legacy", List.of(), "c", "r", null, null,
                "Low", "Standard", true, 1, 1);
        legacy = repository.save(legacy);
        mongo.updateFirst(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(legacy.getId())),
                new org.springframework.data.mongodb.core.query.Update().unset("deliveryEmission"), Product.class);

        assertThat(backfill.backfill()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findById(legacy.getId()).orElseThrow().getDeliveryEmission())
            .isEqualTo(com.wovely.products.models.EDeliveryEmission.LOW);
        assertThat(backfill.backfill()).isZero();
    }
}
