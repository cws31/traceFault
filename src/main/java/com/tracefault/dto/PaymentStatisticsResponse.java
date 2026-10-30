package com.tracefault.dto;

import java.math.BigDecimal;

public class PaymentStatisticsResponse {

    private long totalRecords;
    private long successfulPayments;
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
