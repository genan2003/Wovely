package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.SellerReview;
import com.wovely.wovely.models.User;
import com.wovely.wovely.repository.SellerReviewRepository;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 1.4 (profiles) and 1.7 (seller reviews bound to the caller). */
@WebMvcTest({ UserController.class, PublicUserController.class })
class ProfileAndSellerReviewTest extends SecuredWebMvcTest {

    @MockitoBean SellerReviewRepository sellerReviewRepository;
    @MockitoBean com.wovely.wovely.services.PurchaseVerificationService purchaseVerification;

    @Test
    void userCanReadAndUpdateOwnProfile() throws Exception {
        mvc.perform(get("/api/user/profile/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"));
        mvc.perform(put("/api/user/profile/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Sibiu\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void userCannotReadOrUpdateSomeoneElsesProfile() throws Exception {
        mvc.perform(get("/api/user/profile/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/user/profile/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"makerStory\":\"pwned\"}"))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminCanReadAnyProfile() throws Exception {
        mvc.perform(get("/api/user/profile/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.admin())))
            .andExpect(status().isOk());
    }

    @Test
    void publicSellerProfileAndReviewsAreAnonymous() throws Exception {
        when(sellerReviewRepository.findBySellerId(TestTokens.SELLER_ID)).thenReturn(List.of());
        mvc.perform(get("/api/public/users/seller/" + TestTokens.SELLER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews")).andExpect(status().isOk());
    }

    static final String REVIEW = """
        {"buyerId":"buyer-2","buyerName":"Fake","overallRating":5,"communicationRating":5,"shippingRating":5,"comment":"Great"}""";

    @Test
    void anonymousCannotPostSellerReview() throws Exception {
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews")
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isUnauthorized());
        verify(sellerReviewRepository, never()).save(any());
    }

    @Test
    void sellerReviewAuthorComesFromToken() throws Exception {
        when(sellerReviewRepository.save(any(SellerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isOk());
        ArgumentCaptor<SellerReview> saved = ArgumentCaptor.forClass(SellerReview.class);
        verify(sellerReviewRepository).save(saved.capture());
        assertThat(saved.getValue().getBuyerId()).isEqualTo(TestTokens.BUYER_ID);
        assertThat(saved.getValue().getBuyerName()).isEqualTo("testuser");
        assertThat(saved.getValue().getSellerId()).isEqualTo(TestTokens.SELLER_ID);
    }

    @Test
    void sellerCannotReviewThemselvesAndRatingsAreBounded() throws Exception {
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW.replace("\"overallRating\":5", "\"overallRating\":9")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sellerReviewIsVerifiedOnlyWhenBuyerReceivedAnOrder() throws Exception {
        when(sellerReviewRepository.save(any(SellerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseVerification.hasReceivedFromSeller(TestTokens.BUYER_ID, TestTokens.SELLER_ID)).thenReturn(true);
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verifiedPurchase").value(true));
    }

    @Test
    void clientCannotForgeVerifiedPurchase() throws Exception {
        when(sellerReviewRepository.save(any(SellerReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseVerification.hasReceivedFromSeller(TestTokens.OTHER_BUYER_ID, TestTokens.SELLER_ID)).thenReturn(false);
        String forged = REVIEW.replace("{", "{\"verifiedPurchase\":true,");
        mvc.perform(post("/api/public/users/seller/" + TestTokens.SELLER_ID + "/reviews").header(AUTH, bearer(TestTokens.otherBuyer()))
                .contentType(MediaType.APPLICATION_JSON).content(forged))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verifiedPurchase").value(false));
    }
}
