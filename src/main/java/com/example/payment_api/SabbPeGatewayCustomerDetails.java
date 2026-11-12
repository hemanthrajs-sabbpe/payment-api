package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class SabbPeGatewayCustomerDetails {

    @NotBlank(message = "Customer first name is required")
    @JsonProperty("firstname")
    private String firstname;

    @NotBlank(message = "Customer email is required")
    @JsonProperty("email")
    private String email;

    @NotBlank(message = "Customer phone is required")
    @JsonProperty("phone")
    private String phone;

    public SabbPeGatewayCustomerDetails() {
    }

    public SabbPeGatewayCustomerDetails(String firstname, String email, String phone) {
        this.firstname = firstname;
        this.email = email;
        this.phone = phone;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
