package com.tracefault.controller;

import com.tracefault.dto.ErrorResponse;
import com.tracefault.dto.PaymentRequest;
import com.tracefault.dto.PaymentResponse;
import com.tracefault.dto.PaymentStatisticsResponse;
import com.tracefault.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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
@Tag(name = "Payments", description = "Core payment operations including submission, transaction query, and financial auditing statistics")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Process a payment transaction",
            description = "Submits a payment for an order with optional idempotency protection and post-commit response delay simulation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment processed successfully or existing transaction returned idempotently",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure or missing Idempotency-Key header when protection is enabled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - idempotency key already used for a different payment payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    description = "Unique idempotency identifier to prevent duplicate processing across network retries",
                    example = "KEY-DEMO-001"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(
                    name = "X-Demo-Delay-Response-Ms",
                    in = ParameterIn.HEADER,
                    description = "Simulated delay in milliseconds applied strictly AFTER the database transaction has committed",
                    example = "3000"
            )
            @RequestHeader(value = "X-Demo-Delay-Response-Ms", required = false) Long delayMs) {

        log.info("Incoming POST /api/payments request: orderId='{}', amount={} {}, key='{}', delayMs={}",
                request.getOrderId(), request.getAmount(), request.getCurrency(), idempotencyKey, delayMs);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey, delayMs);
        return ResponseEntity.ok(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve all payment records",
            description = "Fetches an array of all persisted payment transactions in reverse chronological order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List of payments retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))
            )
    })
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping(value = "/by-key/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve payment by idempotency key",
            description = "Fetches a specific payment transaction record by its unique idempotency key."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment found and returned",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found for the supplied key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PaymentResponse> getPaymentByIdempotencyKey(
            @Parameter(name = "key", in = ParameterIn.PATH, description = "The unique idempotency key", example = "KEY-DEMO-001")
            @PathVariable("key") String key) {
        return ResponseEntity.ok(paymentService.getPaymentByIdempotencyKey(key));
    }

    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve payment statistics",
            description = "Calculates aggregated metrics including total payment records, count of successful payments, and total processed currency amount."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment statistics calculated successfully",
                    content = @Content(schema = @Schema(implementation = PaymentStatisticsResponse.class))
            )
    })
    public ResponseEntity<PaymentStatisticsResponse> getPaymentStatistics() {
        return ResponseEntity.ok(paymentService.getStatistics());
    }
}
