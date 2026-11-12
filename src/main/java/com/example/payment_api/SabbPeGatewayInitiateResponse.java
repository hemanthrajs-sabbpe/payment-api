package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SabbPeGatewayInitiateResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("merchant_order_ref")
    private String merchantOrderRef;

    @JsonProperty("payment_url")
    private String paymentUrl;

    @JsonProperty("gateway")
    private String gateway;

    @JsonProperty("txnid")
    private String txnid;

    @JsonProperty("initiation_status")
    private String initiationStatus;

    @JsonProperty("message")
    private String message;

    public SabbPeGatewayInitiateResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    public void setMerchantOrderRef(String merchantOrderRef) {
        this.merchantOrderRef = merchantOrderRef;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getTxnid() {
        return txnid;
    }

    public void setTxnid(String txnid) {
        this.txnid = txnid;
    }

    public String getInitiationStatus() {
        return initiationStatus;
    }

    public void setInitiationStatus(String initiationStatus) {
        this.initiationStatus = initiationStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
