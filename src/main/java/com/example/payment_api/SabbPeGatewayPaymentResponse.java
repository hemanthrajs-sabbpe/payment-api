package com.example.payment_api;

public class SabbPeGatewayPaymentResponse {

    private Long paymentId;
    private String merchantOrderRef;
    private String paymentUrl;
    private Double amount;
    private String status;
    private String transactionId;
    private String upiQrString;
    private String vpa;
    private String mcc;

    public SabbPeGatewayPaymentResponse() {
    }

    public SabbPeGatewayPaymentResponse(Long paymentId, String merchantOrderRef, String paymentUrl,
                                       Double amount, String status, String transactionId) {
        this.paymentId = paymentId;
        this.merchantOrderRef = merchantOrderRef;
        this.paymentUrl = paymentUrl;
        this.amount = amount;
        this.status = status;
        this.transactionId = transactionId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
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

    public String getUpiQrString() {
        return upiQrString;
    }

    public void setUpiQrString(String upiQrString) {
        this.upiQrString = upiQrString;
    }

    public String getVpa() {
        return vpa;
    }

    public void setVpa(String vpa) {
        this.vpa = vpa;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }
}
