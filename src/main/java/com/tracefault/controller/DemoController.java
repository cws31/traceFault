package com.tracefault.controller;

import com.tracefault.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
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
@Tag(name = "Demonstration Control", description = "Controlled failure injection, test database reset, and runtime idempotency mode management")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final PaymentService paymentService;

    public DemoController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Reset demonstration database",
            description = "Safely purges synthetic payment records and synchronizes the unique index with active mode. Available exclusively in non-production environments."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demonstration state reset successfully")
    })
    public ResponseEntity<Map<String, Object>> resetDemo() {
        log.warn("POST /api/demo/reset invoked");
        paymentService.resetDemoData();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demonstration data successfully reset.",
                "idempotencyEnabled", paymentService.isIdempotencyEnabled()
        ));
    }

    @PostMapping(value = "/mode", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Toggle idempotency protection mode",
            description = "Dynamically switches between vulnerable mode (false) and corrected idempotent mode (true) for live reliability demonstrations without JVM restart."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Idempotency protection mode updated successfully")
    })
    public ResponseEntity<Map<String, Object>> setMode(
            @Parameter(name = "idempotencyEnabled", in = ParameterIn.QUERY, description = "True to enable idempotency protection; false for vulnerable demonstration mode", example = "true")
            @RequestParam("idempotencyEnabled") boolean idempotencyEnabled) {
        log.info("POST /api/demo/mode invoked with idempotencyEnabled={}", idempotencyEnabled);
        paymentService.setIdempotencyEnabled(idempotencyEnabled);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Idempotency protection mode updated.",
                "idempotencyEnabled", paymentService.isIdempotencyEnabled()
        ));
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get demonstration status",
            description = "Reports the currently active idempotency mode and total stored payment count."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "idempotencyEnabled", paymentService.isIdempotencyEnabled(),
                "totalRecords", paymentService.getStatistics().getTotalRecords()
        ));
    }
}
