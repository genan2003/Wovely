package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wovely.wovely.models.EOrderStatus;
import com.wovely.wovely.models.Report;
import com.wovely.wovely.models.ReturnRequest;
import com.wovely.wovely.repository.ChatMessageRepository;
import com.wovely.wovely.repository.OrderRepository;
import com.wovely.wovely.repository.ReportRepository;
import com.wovely.wovely.repository.ReturnRequestRepository;
import com.wovely.wovely.support.Orders;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** PLAN.md 1.4 — returns and reports are bound to the caller and the order's participants. */
@WebMvcTest({ ReturnRequestController.class, ReportController.class })
class ReturnAndReportControllerTest extends SecuredWebMvcTest {

    @MockitoBean OrderRepository orderRepository;
    @MockitoBean ReturnRequestRepository returnRequestRepository;
    @MockitoBean ReportRepository reportRepository;
    @MockitoBean ChatMessageRepository chatMessageRepository;

    static final String RETURN = """
        {"orderId":"o-1","buyerId":"buyer-2","sellerId":"seller-2","totalAmount":9999,"status":"REFUNDED",
         "reason":"Wrong colour","items":[{"productId":"p-1","price":9999,"quantity":5}]}""";

    @BeforeEach
    void stubs() {
        when(orderRepository.findById("o-1")).thenReturn(
            Optional.of(Orders.order("o-1", TestTokens.BUYER_ID, TestTokens.SELLER_ID, EOrderStatus.DELIVERED)));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void buyerOpensReturnWithDataTakenFromOrder() throws Exception {
        mvc.perform(post("/api/returns").header(AUTH, bearer(TestTokens.buyer()))
                .contentType(MediaType.APPLICATION_JSON).content(RETURN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PICKED_UP"))
            .andExpect(jsonPath("$.totalAmount").value(20.0));
        ArgumentCaptor<ReturnRequest> saved = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getBuyerId()).isEqualTo(TestTokens.BUYER_ID);
        assertThat(saved.getValue().getSellerId()).isEqualTo(TestTokens.SELLER_ID);
        assertThat(saved.getValue().getItems()).singleElement().satisfies(i -> assertThat(i.getPrice()).isEqualTo(10.0));
    }

    @Test
    void otherBuyerCannotOpenReturnOnSomeoneElsesOrder() throws Exception {
        mvc.perform(post("/api/returns").header(AUTH, bearer(TestTokens.otherBuyer()))
                .contentType(MediaType.APPLICATION_JSON).content(RETURN))
            .andExpect(status().isForbidden());
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void onlyTheOrdersSellerUpdatesReturnStatus() throws Exception {
        ReturnRequest r = new ReturnRequest();
        r.setId("r-1");
        r.setSellerId(TestTokens.SELLER_ID);
        when(returnRequestRepository.findById("r-1")).thenReturn(Optional.of(r));

        mvc.perform(patch("/api/returns/r-1/status").header(AUTH, bearer(TestTokens.otherSeller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARRIVED\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/returns/r-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARRIVED\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/returns/r-1/status").header(AUTH, bearer(TestTokens.seller()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WHATEVER\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnListsAreSelfOnly() throws Exception {
        mvc.perform(get("/api/returns/buyer/" + TestTokens.BUYER_ID).header(AUTH, bearer(TestTokens.otherBuyer())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/returns/seller/" + TestTokens.SELLER_ID).header(AUTH, bearer(TestTokens.otherSeller())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/returns/all").header(AUTH, bearer(TestTokens.seller())))
            .andExpect(status().isForbidden());
    }

    @Test
    void reporterIsTakenFromToken() throws Exception {
        mvc.perform(post("/api/reports").header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reporterId\":\"someone-else\",\"targetType\":\"CHAT\",\"targetId\":\""
                        + TestTokens.BUYER_ID + ":" + TestTokens.SELLER_ID + "\",\"reason\":\"spam\",\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reporterId").value(TestTokens.BUYER_ID))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void cannotReportAConversationYouAreNotPartOf() throws Exception {
        mvc.perform(post("/api/reports").header(AUTH, bearer(TestTokens.otherBuyer())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"CHAT\",\"targetId\":\"" + TestTokens.BUYER_ID + ":" + TestTokens.SELLER_ID
                        + "\",\"reason\":\"snooping\"}"))
            .andExpect(status().isForbidden());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportQueueIsAdminOnly() throws Exception {
        mvc.perform(get("/api/reports/all").header(AUTH, bearer(TestTokens.buyer()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/all").header(AUTH, bearer(TestTokens.admin()))).andExpect(status().isOk());
    }
}
