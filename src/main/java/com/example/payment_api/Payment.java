package com.example.payment_api;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private Double amount;

    @NotBlank(message = "Status is required")
    private String status;

    @Column(unique = true)
    private String transactionId;

    // SabbPe merchant order reference
    @Column(unique = true)
    private String merchantOrderRef;

    // SabbPe Pay By Link reference
    @Column(unique = true)
    private String pblOrderRef;

    // SabbPe Gateway hosted payment page URL
    @Column(length = 1024)
    private String paymentUrl;

    // SabbPe Gateway master transaction identifier from callback
    private String masterTransactionId;

    // Payment method used (e.g., UPI, CARD) from callback
    private String paymentMethod;

    // Gateway / provider identifier (e.g., SABBPE_GATEWAY, SABBPE_PBL, EASEBUZZ)
    private String gatewayProvider;

    public Payment() {
    }

    public Payment(String customerName, Double amount, String status) {
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
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

    public String getPblOrderRef() {
        return pblOrderRef;
    }

    public void setPblOrderRef(String pblOrderRef) {
        this.pblOrderRef = pblOrderRef;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public String getMasterTransactionId() {
        return masterTransactionId;
    }

    public void setMasterTransactionId(String masterTransactionId) {
        this.masterTransactionId = masterTransactionId;
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
}
