package equ.clt.handler.dreamkas;

import java.io.Serializable;

public class DreamkasSettings implements Serializable {

    private String baseURL = "";
    private String token = "";
    private String uuidSuffix = null;
    private Integer salesHours = 0;
    private Integer salesLimitReceipt = 100;
    private Integer stepSend = 100;
    //если задано, чтение реализации вызывается только каждый указанный раз
    private Integer runReadSalesInterval;

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

    public String getUuidSuffix() {
        return uuidSuffix;
    }

    public void setUuidSuffix(String uuidSuffix) {
        this.uuidSuffix = uuidSuffix;
    }

    public Integer getSalesHours() {
        return salesHours;
    }

    public void setSalesHours(Integer salesHours) {
        this.salesHours = salesHours;
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

    public Integer getRunReadSalesInterval() {
        return runReadSalesInterval;
    }

    public void setRunReadSalesInterval(Integer runReadSalesInterval) {
        this.runReadSalesInterval = runReadSalesInterval;
    }
}
