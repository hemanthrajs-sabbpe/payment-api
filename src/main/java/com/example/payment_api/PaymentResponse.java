
package com.example.payment_api;

public class PaymentResponse {

    private Long id;
    private String customerName;
    private Double amount;
    private String status;
    private String transactionId;
    private String merchantOrderRef;
    private String pblOrderRef;
    private String paymentUrl;
    private String paymentMethod;
    private String gatewayProvider;
    private String masterTransactionId;
    private String receiptToken;

    public PaymentResponse() {
    }

    // Keep the existing constructor for compatibility.
    public PaymentResponse(
            Long id,
            String customerName,
            Double amount,
            String status) {

        this(id, customerName, amount, status, null);
    }

    // Constructor including the transaction ID.
    public PaymentResponse(
            Long id,
            String customerName,
            Double amount,
            String status,
            String transactionId) {
        this(id, customerName, amount, status, transactionId, null, null);
    }

    public PaymentResponse(
            Long id,
            String customerName,
            Double amount,
            String status,
            String transactionId,
            String merchantOrderRef,
            String pblOrderRef) {
        this.id = id;
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
        this.transactionId = transactionId;
        this.merchantOrderRef = merchantOrderRef;
        this.pblOrderRef = pblOrderRef;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Double getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    public String getPblOrderRef() {
        return pblOrderRef;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public String getMasterTransactionId() {
        return masterTransactionId;
    }

    public void setMasterTransactionId(String masterTransactionId) {
        this.masterTransactionId = masterTransactionId;
    }

    public String getReceiptToken() {
        return receiptToken;
    }

    public void setReceiptToken(String receiptToken) {
        this.receiptToken = receiptToken;
    }
}
