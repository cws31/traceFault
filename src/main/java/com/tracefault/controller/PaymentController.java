package com.tracefault.controller;

import com.tracefault.dto.PaymentRequest;
import com.tracefault.dto.PaymentResponse;
import com.tracefault.dto.PaymentStatisticsResponse;
import com.tracefault.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Demo-Delay-Response-Ms", required = false) Long delayMs) {

        log.info("Incoming POST /api/payments request: orderId='{}', amount={} {}, key='{}', delayMs={}",
                request.getOrderId(), request.getAmount(), request.getCurrency(), idempotencyKey, delayMs);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey, delayMs);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/by-key/{key}")
    public ResponseEntity<PaymentResponse> getPaymentByIdempotencyKey(@PathVariable("key") String key) {
        return ResponseEntity.ok(paymentService.getPaymentByIdempotencyKey(key));
    }

    @GetMapping("/statistics")
    public ResponseEntity<PaymentStatisticsResponse> getPaymentStatistics() {
        return ResponseEntity.ok(paymentService.getStatistics());
    }
}
