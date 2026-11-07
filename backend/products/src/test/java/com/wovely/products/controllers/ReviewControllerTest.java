package com.wovely.products.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.products.models.Review;
import com.wovely.products.repository.ReviewRepository;
import com.wovely.products.support.SecuredWebMvcTest;
import com.wovely.products.support.TestTokens;

/** PLAN.md 1.7 / 3.3 — reviews require login, are bound to the caller, one per product, verified by core. */
@WebMvcTest(ReviewController.class)
class ReviewControllerTest extends SecuredWebMvcTest {

    @MockitoBean ReviewRepository reviewRepository;
    @MockitoBean com.wovely.products.clients.PurchaseVerificationClient purchaseVerification;

    static final String REVIEW = """
        {"productId":"p-1","userId":"buyer-2","username":"Impostor","rating":5,"comment":"Great"}""";

    @Test
    void anonymousCannotReview() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isUnauthorized());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void reviewAuthorComesFromToken() throws Exception {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isCreated());
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(TestTokens.BUYER_ID);
        assertThat(saved.getValue().getUsername()).isEqualTo("testuser");
    }

    @Test
    void secondReviewOfSameProductIsConflict() throws Exception {
        when(reviewRepository.existsByProductIdAndUserId("p-1", TestTokens.BUYER_ID)).thenReturn(true);
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isConflict());
    }

    @Test
    void ratingOutOfRangeIsBadRequest() throws Exception {
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW.replace("\"rating\":5", "\"rating\":9")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void readingReviewsIsPublic() throws Exception {
        when(reviewRepository.findByProductId("p-1")).thenReturn(List.of());
        mvc.perform(get("/api/reviews/product/p-1")).andExpect(status().isOk());
    }

    @Test
    void reviewIsVerifiedWhenCoreConfirmsTheBuyerReceivedIt() throws Exception {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseVerification.hasReceived(TestTokens.BUYER_ID, "p-1")).thenReturn(true);
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verifiedPurchase").value(true));
    }

    @Test
    void clientCannotForgeVerifiedPurchase() throws Exception {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseVerification.hasReceived(TestTokens.BUYER_ID, "p-1")).thenReturn(false);
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW.replace("{", "{\"verifiedPurchase\":true,")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verifiedPurchase").value(false));
    }

    @Test
    void verificationIsNotCalledForRejectedReviews() throws Exception {
        when(reviewRepository.existsByProductIdAndUserId("p-1", TestTokens.BUYER_ID)).thenReturn(true);
        mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isConflict());
        verify(purchaseVerification, never()).hasReceived(any(), any());
    }

    @Test
    void reviewsAreValidated() throws Exception {
        String sevenPhotos = "[" + String.join(",", java.util.Collections.nCopies(7, "\"https://x.test/a.jpg\"")) + "]";
        for (String body : new String[] { "{\"productId\":\"p-1\"}", "{\"productId\":\"p-1\",\"rating\":0}",
                "{\"rating\":4}", "{\"productId\":\"p-1\",\"rating\":4,\"photoUrls\":" + sevenPhotos + "}" }) {
            mvc.perform(post("/api/reviews").header(AUTH, bearer(TestTokens.buyer()))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verify(reviewRepository, never()).save(any());
    }
}
