
package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EasebuzzCallbackRequest {

    private String status;
    private String txnid;
    private String amount;
    private String hash;
    private String email;
    private String firstname;
    private String productinfo;
    private String key;

    @JsonProperty("udf1")
    private String udf1;
    @JsonProperty("udf2")
    private String udf2;
    @JsonProperty("udf3")
    private String udf3;
    @JsonProperty("udf4")
    private String udf4;
    @JsonProperty("udf5")
    private String udf5;
    @JsonProperty("udf6")
    private String udf6;
    @JsonProperty("udf7")
    private String udf7;
    @JsonProperty("udf8")
    private String udf8;
    @JsonProperty("udf9")
    private String udf9;
    @JsonProperty("udf10")
    private String udf10;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTxnid() { return txnid; }
    public void setTxnid(String txnid) { this.txnid = txnid; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }

    public String getProductinfo() { return productinfo; }
    public void setProductinfo(String productinfo) { this.productinfo = productinfo; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUdf1() { return udf1; }
    public void setUdf1(String udf1) { this.udf1 = udf1; }
    public String getUdf2() { return udf2; }
    public void setUdf2(String udf2) { this.udf2 = udf2; }
    public String getUdf3() { return udf3; }
    public void setUdf3(String udf3) { this.udf3 = udf3; }
    public String getUdf4() { return udf4; }
    public void setUdf4(String udf4) { this.udf4 = udf4; }
    public String getUdf5() { return udf5; }
    public void setUdf5(String udf5) { this.udf5 = udf5; }
    public String getUdf6() { return udf6; }
    public void setUdf6(String udf6) { this.udf6 = udf6; }
    public String getUdf7() { return udf7; }
    public void setUdf7(String udf7) { this.udf7 = udf7; }
    public String getUdf8() { return udf8; }
    public void setUdf8(String udf8) { this.udf8 = udf8; }
    public String getUdf9() { return udf9; }
    public void setUdf9(String udf9) { this.udf9 = udf9; }
    public String getUdf10() { return udf10; }
    public void setUdf10(String udf10) { this.udf10 = udf10; }
}
