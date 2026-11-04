package com.wovely.wovely.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.services.WalletService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 3.7 — the caller's own wallet only; top-up takes an amount, never card data. */
@WebMvcTest(WalletController.class)
class WalletControllerTest extends SecuredWebMvcTest {

    @MockitoBean WalletService wallet;

    @Test
    void balanceIsTheCallersOwn() throws Exception {
        when(wallet.balanceCents(TestTokens.BUYER_ID)).thenReturn(2750L);
        mvc.perform(get("/api/wallet").header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceCents").value(2750))
            .andExpect(jsonPath("$.balance").value(27.5))
            .andExpect(jsonPath("$.currency").value("RON"));
    }

    @Test
    void topUpCreditsTheCaller() throws Exception {
        when(wallet.balanceCents(TestTokens.SELLER_ID)).thenReturn(10000L);
        mvc.perform(post("/api/wallet/top-up").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100,\"userId\":\"buyer-2\",\"cardNumber\":\"4111\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balanceCents").value(10000));
        verify(wallet).topUp(TestTokens.SELLER_ID, new BigDecimal("100"));
    }

    @Test
    void invalidTopUps() throws Exception {
        mvc.perform(post("/api/wallet/top-up").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        when(wallet.topUp(any(), any())).thenThrow(ApiException.badRequest("amount must be between 0.01 and 5000"));
        mvc.perform(post("/api/wallet/top-up").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wallet/top-up").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
            .andExpect(status().isUnauthorized());
        verify(wallet, never()).topUp(any(), any());
    }
}
