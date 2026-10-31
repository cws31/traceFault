package com.tracefault.dto;

import com.tracefault.entity.Payment;
import com.tracefault.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Outbound payment receipt representing persisted transaction state and deduplication status")
public class PaymentResponse {

    @Schema(description = "Internal database transaction primary key", example = "1")
    private Long id;

    @Schema(description = "Associated order identifier", example = "ORDER-101")
    private String orderId;

    @Schema(description = "Processed monetary amount", example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "Three-letter ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "Idempotency key associated with this transaction", example = "KEY-DEMO-001")
    private String idempotencyKey;

    @Schema(description = "Current lifecycle status of the payment", example = "SUCCESS")
    private PaymentStatus paymentStatus;

    @Schema(description = "Timestamp when the payment record was created", example = "2026-10-31T16:00:00Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the payment record was last updated", example = "2026-10-31T16:00:00Z")
    private Instant updatedAt;

    @Schema(description = "Indicates whether this response was served from a previously committed transaction due to idempotent deduplication", example = "false")
    private boolean duplicate;

    public PaymentResponse() {
    }

    public PaymentResponse(Long id, String orderId, BigDecimal amount, String currency,
                           String idempotencyKey, PaymentStatus paymentStatus,
                           Instant createdAt, Instant updatedAt, boolean duplicate) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.paymentStatus = paymentStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.duplicate = duplicate;
    }

    public static PaymentResponse from(Payment payment, boolean duplicate) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getIdempotencyKey(),
                payment.getPaymentStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                duplicate
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    @Override
    public String toString() {
        return "PaymentResponse{" +
                "id=" + id +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", paymentStatus=" + paymentStatus +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", duplicate=" + duplicate +
                '}';
    }
}
