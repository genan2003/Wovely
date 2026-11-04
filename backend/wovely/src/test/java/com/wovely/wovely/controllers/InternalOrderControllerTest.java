package com.wovely.wovely.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.services.PurchaseVerificationService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 3.3 — {@code /internal/orders/verify-purchase} is service-to-service only. */
@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest extends SecuredWebMvcTest {

    static final String KEY = "test-internal-key";
    static final String URL = "/internal/orders/verify-purchase?buyerId=buyer-1&productId=p-1";

    @MockitoBean PurchaseVerificationService verification;

    @Test
    void withServiceKeyReturnsVerdict() throws Exception {
        when(verification.hasReceivedProduct("buyer-1", "p-1")).thenReturn(true);
        mvc.perform(get(URL).header("X-Internal-Api-Key", KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void missingParamIsBadRequest() throws Exception {
        mvc.perform(get("/internal/orders/verify-purchase?buyerId=buyer-1").header("X-Internal-Api-Key", KEY))
            .andExpect(status().isBadRequest());
    }

    @Test
    void notReachableWithoutKeyEvenAsAdmin() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isForbidden());
        mvc.perform(get(URL).header("X-Internal-Api-Key", "wrong")).andExpect(status().isUnauthorized());
        verify(verification, never()).hasReceivedProduct(any(), any());
    }
}
