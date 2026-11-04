package com.wovely.wovely.models;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable ledger line; {@code amountCents} is signed (+ credit, − debit). */
@Document(collection = "wallet_transactions")
@CompoundIndex(name = "user_time", def = "{'userId': 1, 'createdAt': -1}")
public class WalletTransaction {
    @Id
    private String id;
    private String userId;
    private EWalletTransactionType type;
    private long amountCents;
    private long balanceAfterCents;
    private String orderId;
    private String description;
    private Date createdAt;

    public WalletTransaction() {
    }

    public WalletTransaction(String userId, EWalletTransactionType type, long amountCents, long balanceAfterCents,
            String orderId, String description) {
        this.userId = userId;
        this.type = type;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.orderId = orderId;
        this.description = description;
        this.createdAt = new Date();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public EWalletTransactionType getType() { return type; }
    public long getAmountCents() { return amountCents; }
    public long getBalanceAfterCents() { return balanceAfterCents; }
    public String getOrderId() { return orderId; }
    public String getDescription() { return description; }
    public Date getCreatedAt() { return createdAt; }
}
