package com.wovely.wovely.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/replace an address-book entry. {@code makeDefault} = use it by default at checkout. */
public record AddressRequest(
        @Size(max = 40) String label,
        @NotBlank(message = "recipientName is required") @Size(max = 100) String recipientName,
        @NotBlank(message = "street is required") @Size(max = 200) String street,
        @NotBlank(message = "city is required") @Size(max = 100) String city,
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "postalCode may contain letters, digits, spaces and dashes") String postalCode,
        @NotBlank(message = "country is required") @Size(max = 60) String country,
        @Size(max = 30) @Pattern(regexp = "^[0-9 +()-]*$", message = "phone may contain digits, spaces and + ( ) -") String phone,
        Boolean makeDefault) {
}
