package com.tracefault.controller;

import com.tracefault.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@Profile("!prod")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final PaymentService paymentService;

    public DemoController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetDemo() {
        log.warn("POST /api/demo/reset invoked");
        paymentService.resetDemoData();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demonstration data successfully reset.",
                "idempotencyEnabled", paymentService.isIdempotencyEnabled()));
    }

    @PostMapping("/mode")
    public ResponseEntity<Map<String, Object>> setMode(@RequestParam("idempotencyEnabled") boolean idempotencyEnabled) {
        log.info("POST /api/demo/mode invoked with idempotencyEnabled={}", idempotencyEnabled);
        paymentService.setIdempotencyEnabled(idempotencyEnabled);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Idempotency protection mode updated.",
                "idempotencyEnabled", paymentService.isIdempotencyEnabled()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "idempotencyEnabled", paymentService.isIdempotencyEnabled(),
                "totalRecords", paymentService.getStatistics().getTotalRecords()));
    }
}
