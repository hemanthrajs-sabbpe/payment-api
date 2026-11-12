package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class SabbPeGatewayInitiateRequest {

    @NotBlank(message = "sabbpe_token is required")
    @JsonProperty("sabbpe_token")
    private String sabbpeToken;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    @JsonProperty("amount")
    private Double amount;

    @NotBlank(message = "productinfo is required")
    @JsonProperty("productinfo")
    private String productinfo;

    @NotBlank(message = "frontend_url is required")
    @JsonProperty("frontend_url")
    private String frontendUrl;

    @NotNull(message = "customer is required")
    @Valid
    @JsonProperty("customer")
    private SabbPeGatewayCustomerDetails customer;

    public SabbPeGatewayInitiateRequest() {
    }

    public SabbPeGatewayInitiateRequest(String sabbpeToken, Double amount, String productinfo,
                                       String frontendUrl, SabbPeGatewayCustomerDetails customer) {
        this.sabbpeToken = sabbpeToken;
        this.amount = amount;
        this.productinfo = productinfo;
        this.frontendUrl = frontendUrl;
        this.customer = customer;
    }

    public String getSabbpeToken() {
        return sabbpeToken;
    }

    public void setSabbpeToken(String sabbpeToken) {
        this.sabbpeToken = sabbpeToken;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getProductinfo() {
        return productinfo;
    }

    public void setProductinfo(String productinfo) {
        this.productinfo = productinfo;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public SabbPeGatewayCustomerDetails getCustomer() {
        return customer;
    }

    public void setCustomer(SabbPeGatewayCustomerDetails customer) {
        this.customer = customer;
    }
}
