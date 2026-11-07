package com.wovely.wovely.controllers;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.EDeliveryOption;

/** Public price list for delivery, so the SPA never hard-codes what checkout charges. */
@RestController
@RequestMapping("/api/public/delivery-options")
public class DeliveryOptionController {

    public record DeliveryOptionView(String id, String label, java.math.BigDecimal fee, long feeCents, double co2Kg, int estimatedDays) {
    }

    @GetMapping
    public List<DeliveryOptionView> list() {
        return Arrays.stream(EDeliveryOption.values())
                .map(o -> new DeliveryOptionView(o.name(), o.label(), o.fee(), o.feeCents(), o.co2Kg(), o.estimatedDays()))
                .toList();
    }
}
