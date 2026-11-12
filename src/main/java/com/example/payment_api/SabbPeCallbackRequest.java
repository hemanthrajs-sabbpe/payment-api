package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SabbPeCallbackRequest(

        String gateway,

        String product,

        @JsonProperty("pbl_order_ref")
        String pblOrderRef,

        @JsonProperty("merchant_order_ref")
        String merchantOrderRef,

        @JsonProperty("master_transaction_id")
        String masterTransactionId,

        @JsonProperty("payment_status")
        String paymentStatus,

        BigDecimal amount,

        String currency,

        @JsonProperty("payment_completed_at")
        String paymentCompletedAt,

        @JsonProperty("payment_method")
        String paymentMethod,

        String rrn
) {}