package com.wovely.products.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.products.clients.AuditClient;
import com.wovely.products.models.Category;
import com.wovely.products.models.Product;
import com.wovely.products.repository.CategoryRepository;
import com.wovely.products.repository.ProductRepository;
import com.wovely.products.services.CatalogQueryService;
import com.wovely.products.services.ListingService;
import com.wovely.products.services.SellerListingQueries;
import com.wovely.products.services.StockService;
import com.wovely.products.support.SecuredWebMvcTest;
import com.wovely.products.support.TestTokens;

/** Listing review on seller edits, seller views, admin queue and category validation over HTTP. */
@WebMvcTest({ ProductController.class, AdminProductController.class, CategoryController.class })
@Import({ ListingService.class, SellerListingQueries.class })
class ListingControllerTest extends SecuredWebMvcTest {

    static final String BASE = "/api/products/seller/" + TestTokens.SELLER_ID;

    @MockitoBean ProductRepository productRepository;
    @MockitoBean CategoryRepository categoryRepository;
    @MockitoBean StockService stockService;
    @MockitoBean CatalogQueryService catalogQuery;
    @MockitoBean AuditClient auditClient;

    Product mug;

    @BeforeEach
    void stubs() {
        mug = new Product();
        mug.setId("p-1");
        mug.setSellerId(TestTokens.SELLER_ID);
        mug.setName("Blue mug");
        mug.setPriceCents(8000L);
        mug.setCategory("Ceramics");
        mug.setMaterials(List.of("clay"));
        mug.setStockQuantity(2);
        mug.setLowStockThreshold(5);
        mug.setStatus(ListingService.APPROVED);
        when(productRepository.findBySellerIdAndId(TestTokens.SELLER_ID, "p-1")).thenReturn(Optional.of(mug));
        when(productRepository.findById("p-1")).thenReturn(Optional.of(mug));
        when(productRepository.findBySellerIdAndStatusNot(TestTokens.SELLER_ID, Product.ARCHIVED)).thenReturn(List.of(mug));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    String seller() {
        return bearer(TestTokens.seller());
    }

    // ---------------------------------------------------------------- review on edit (regression)

    @Test
    void contentEditOfAnApprovedListingReturnsItToTheQueue() throws Exception {
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Indigo mug\",\"price\":80.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.materials[0]").value("clay")); // not wiped by a partial edit
        verify(auditClient).record(eq("PRODUCT_RESUBMITTED"), eq("p-1"), any());
    }

    @Test
    void needsChangesListingIsResubmittedOnEdit() throws Exception {
        mug.setStatus(ListingService.NEEDS_CHANGES);
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Sharper photos now\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectedListingEditIs409() throws Exception {
        mug.setStatus(ListingService.REJECTED);
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Again\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidEditsAre400() throws Exception {
        for (String body : List.of("{\"price\":-1}", "{\"price\":12.345}", "{\"price\":100000000}", "{\"stockQuantity\":-3}", "{\"latitude\":120}", "{\"name\":\"   \"}",
                "{\"materials\":[\"\"]}")) {
            mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verify(productRepository, never()).save(any());
    }

    @Test
    void priceIsStoredInCentsAndShownInBothForms() throws Exception {
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":42.5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(4250))
            .andExpect(jsonPath("$.price").value(42.5));
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceCents\":1}")) // read-only from the client's side: ignored, not an override
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(4250));
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":90.0,\"status\":\"APPROVED\",\"sellerId\":\"seller-2\",\"deliveryEmission\":\"ZERO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        mug.setStatus(ListingService.NEEDS_CHANGES);
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
            .andExpect(jsonPath("$.status").value("PENDING")); // a seller can't approve their own listing
    }

    @Test
    void handmadeFlagIsAcceptedUnderBothNames() throws Exception {
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"isHandmade\":false}"))
            .andExpect(jsonPath("$.isHandmade").value(false));
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"handmade\":true}"))
            .andExpect(jsonPath("$.isHandmade").value(true));
    }

    @Test
    void anotherSellerCannotEdit() throws Exception {
        mvc.perform(put(BASE + "/product/p-1").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine\"}"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- seller views

    @Test
    void sellerViewsReturnTheirShapes() throws Exception {
        mvc.perform(get(BASE).header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.pendingReviewCount").value(0));
        mvc.perform(get(BASE + "/dashboard").header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.lowStock").value(1)).andExpect(jsonPath("$.categoryBreakdown.Ceramics").value(1));
        mvc.perform(get(BASE + "/low-stock").header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.alertType").value("LOW_STOCK")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(BASE + "/out-of-stock").header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        when(productRepository.findBySellerIdAndCategoryAndStatusNot(TestTokens.SELLER_ID, "Ceramics", Product.ARCHIVED))
            .thenReturn(List.of(mug));
        mvc.perform(get(BASE + "/category/Ceramics").header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("Ceramics"));
        mvc.perform(get(BASE + "/product/p-1").header(AUTH, seller())).andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value("p-1"));
    }

    @Test
    void sellerViewsAreOwnerOnlyAndUnknownProductIs404() throws Exception {
        for (String path : List.of("", "/dashboard", "/low-stock", "/out-of-stock", "/category/Ceramics", "/product/p-1")) {
            mvc.perform(get(BASE + path).header(AUTH, bearer(TestTokens.otherSeller()))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + path).header(AUTH, bearer(TestTokens.buyer()))).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "/product/nope").header(AUTH, seller()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/products/nope")).andExpect(status().isNotFound());
    }

    @Test
    void stockEndpointsWithoutABodyAre400NotA500() throws Exception {
        when(stockService.restock(TestTokens.SELLER_ID, "p-1", null))
            .thenThrow(com.wovely.common.error.ApiException.badRequest("Valid quantity is required"));
        mvc.perform(post(BASE + "/product/p-1/restock").header(AUTH, seller())).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- admin queue

    @Test
    void emptyAdminListsAre200WithAnArray() throws Exception {
        when(productRepository.findByStatus("PENDING")).thenReturn(List.of());
        mvc.perform(get("/api/products/admin/pending").header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$").isEmpty());
        when(productRepository.findBySellerId(TestTokens.SELLER_ID)).thenReturn(List.of(mug));
        mvc.perform(get("/api/products/admin/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("p-1"));
    }

    @Test
    void adminEditKeepsTheStatusAndUnknownIs404() throws Exception {
        mvc.perform(put("/api/products/admin/p-1").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Clean title\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(put("/api/products/admin/nope/status").header(AUTH, bearer(TestTokens.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/products/admin/p-1/status").header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- categories

    @Test
    void adminCreatesACategoryWithDerivedSlugAndLevel() throws Exception {
        Category parent = new Category("Home & Living", null, "home-living", 0);
        parent.setId("c-root");
        when(categoryRepository.findById("c-root")).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/categories").header(AUTH, bearer(TestTokens.admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" Țesături & Covoare \",\"parentId\":\"c-root\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("tesaturi-covoare"))
            .andExpect(jsonPath("$.level").value(1))
            .andExpect(jsonPath("$.name").value("Țesături & Covoare"));
    }

    @Test
    void categoryValidationAndConflicts() throws Exception {
        String admin = bearer(TestTokens.admin());
        mvc.perform(post("/api/categories").header(AUTH, admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/categories").header(AUTH, admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rugs\",\"slug\":\"Not A Slug\"}"))
            .andExpect(status().isBadRequest());
        when(categoryRepository.findById("ghost")).thenReturn(Optional.empty());
        mvc.perform(post("/api/categories").header(AUTH, admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rugs\",\"parentId\":\"ghost\"}"))
            .andExpect(status().isBadRequest());
        when(categoryRepository.findBySlug("rugs")).thenReturn(new Category("Rugs", null, "rugs", 0));
        mvc.perform(post("/api/categories").header(AUTH, admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rugs\"}"))
            .andExpect(status().isConflict());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void categoryReadsArePublic() throws Exception {
        when(categoryRepository.findByParentId(null)).thenReturn(List.of(new Category("Home", null, "home", 0)));
        mvc.perform(get("/api/categories/roots")).andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("home"));
        mvc.perform(get("/api/categories/children/c-root")).andExpect(status().isOk());
    }
}
