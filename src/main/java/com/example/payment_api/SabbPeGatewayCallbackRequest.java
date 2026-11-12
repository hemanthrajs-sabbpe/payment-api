package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SabbPeGatewayCallbackRequest {

    @JsonProperty("gateway")
    private String gateway;

    @JsonProperty("master_transaction_id")
    private String masterTransactionId;

    @JsonProperty("merchant_order_ref")
    private String merchantOrderRef;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("payment_completed_at")
    private String paymentCompletedAt;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("rrn")
    private String rrn;

    public SabbPeGatewayCallbackRequest() {
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getMasterTransactionId() {
        return masterTransactionId;
    }

    public void setMasterTransactionId(String masterTransactionId) {
        this.masterTransactionId = masterTransactionId;
    }

    public String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    public void setMerchantOrderRef(String merchantOrderRef) {
        this.merchantOrderRef = merchantOrderRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentCompletedAt() {
        return paymentCompletedAt;
    }

    public void setPaymentCompletedAt(String paymentCompletedAt) {
        this.paymentCompletedAt = paymentCompletedAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getRrn() {
        return rrn;
    }

    public void setRrn(String rrn) {
        this.rrn = rrn;
    }
}
