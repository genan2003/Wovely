package com.wovely.wovely.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.WalletTransaction;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.services.WalletService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The caller's own mock wallet (PLAN.md 3.7). No user id in the path → no IDOR surface. */
@RestController
@RequestMapping("/api/wallet")
@PreAuthorize("isAuthenticated()")
public class WalletController {

    /** Mock top-up: only an amount — card numbers are never accepted or stored. */
    public record TopUpRequest(@NotNull(message = "amount is required") BigDecimal amount) {
    }

    private final WalletService wallet;

    public WalletController(WalletService wallet) {
        this.wallet = wallet;
    }

    @GetMapping
    public Map<String, Object> balance(@AuthenticationPrincipal UserDetailsImpl me) {
        return view(me.getId());
    }

    @GetMapping("/transactions")
    public List<WalletTransaction> transactions(@RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetailsImpl me) {
        return wallet.transactions(me.getId(), limit);
    }

    @PostMapping("/top-up")
    public Map<String, Object> topUp(@Valid @RequestBody TopUpRequest request, @AuthenticationPrincipal UserDetailsImpl me) {
        wallet.topUp(me.getId(), request.amount());
        return view(me.getId());
    }

    private Map<String, Object> view(String userId) {
        long cents = wallet.balanceCents(userId);
        return Map.of("balanceCents", cents, "balance", BigDecimal.valueOf(cents, 2), "currency", "RON");
    }
}
