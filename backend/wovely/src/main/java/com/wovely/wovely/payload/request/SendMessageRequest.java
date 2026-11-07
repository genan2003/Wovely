package com.wovely.wovely.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chat message from the signed-in user; the sender always comes from the token. */
public record SendMessageRequest(
        @NotBlank(message = "receiverId is required") @Size(max = 64) String receiverId,
        @Size(max = 64) String productId,
        @Size(max = 2048) String productThumbnail,
        @NotBlank(message = "content is required") @Size(max = 2000, message = "messages are limited to 2000 characters") String content) {
}
