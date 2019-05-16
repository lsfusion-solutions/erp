package equ.clt.handler.dreamkas;

import java.io.Serializable;

public class DreamkasSettings implements Serializable {

    private String baseURL = "";
    private String token = "";
    private Integer salesDays = 0;
    private Integer salesLimitReceipt = 100;
    private Integer stepSend = 100;

    public DreamkasSettings () {

    }

    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getSalesDays() {
        return salesDays;
    }

    public void setSalesDays(Integer salesDays) {
        this.salesDays = salesDays;
    }

    public Integer getSalesLimitReceipt() {
        return salesLimitReceipt;
    }

    public void setSalesLimitReceipt(Integer salesLimitReceipt) {
        this.salesLimitReceipt = salesLimitReceipt;
    }

    public Integer getStepSend() {
        return stepSend;
    }

    public void setStepSend(Integer stepSend) {
        this.stepSend = stepSend;
    }
}
