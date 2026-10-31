package com.tracefault.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Aggregated transaction metrics reflecting current database state")
public class PaymentStatisticsResponse {

    @Schema(description = "Total number of payment records currently stored in the database", example = "2")
    private long totalRecords;

    @Schema(description = "Count of payments with SUCCESS status", example = "2")
    private long successfulPayments;

    @Schema(description = "Sum of processed amounts across all successful transactions", example = "2000.00")
    private BigDecimal totalProcessedAmount;

    public PaymentStatisticsResponse() {
    }

    public PaymentStatisticsResponse(long totalRecords, long successfulPayments, BigDecimal totalProcessedAmount) {
        this.totalRecords = totalRecords;
        this.successfulPayments = successfulPayments;
        this.totalProcessedAmount = totalProcessedAmount;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public long getSuccessfulPayments() {
        return successfulPayments;
    }

    public void setSuccessfulPayments(long successfulPayments) {
        this.successfulPayments = successfulPayments;
    }

    public BigDecimal getTotalProcessedAmount() {
        return totalProcessedAmount;
    }

    public void setTotalProcessedAmount(BigDecimal totalProcessedAmount) {
        this.totalProcessedAmount = totalProcessedAmount;
    }

    @Override
    public String toString() {
        return "PaymentStatisticsResponse{" +
                "totalRecords=" + totalRecords +
                ", successfulPayments=" + successfulPayments +
                ", totalProcessedAmount=" + totalProcessedAmount +
                '}';
    }
}
