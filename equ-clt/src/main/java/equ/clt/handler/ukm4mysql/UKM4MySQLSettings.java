package equ.clt.handler.ukm4mysql;

import java.io.Serializable;

public class UKM4MySQLSettings implements Serializable{

    private String importConnectionString;
    private String exportConnectionString;
    private String cashPayments;
    private String cardPayments;
    private String giftCardPayments;
    private String user;
    private String password;
    private Integer timeout;
    private Boolean skipItems;
    private Boolean skipBarcodes;

    public UKM4MySQLSettings() {
    }

    public String getImportConnectionString() {
        return importConnectionString;
    }

    public void setImportConnectionString(String importConnectionString) {
        this.importConnectionString = importConnectionString;
    }

    public String getExportConnectionString() {
        return exportConnectionString;
    }

    public void setExportConnectionString(String exportConnectionString) {
        this.exportConnectionString = exportConnectionString;
    }

    public String getCashPayments() {
        return cashPayments;
    }

    public void setCashPayments(String cashPayments) {
        this.cashPayments = cashPayments;
    }

    public String getCardPayments() {
        return cardPayments;
    }

    public void setCardPayments(String cardPayments) {
        this.cardPayments = cardPayments;
    }

    public String getGiftCardPayments() {
        return giftCardPayments;
    }

    public void setGiftCardPayments(String giftCardPayments) {
        this.giftCardPayments = giftCardPayments;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getSkipItems() {
        return skipItems;
    }

    public void setSkipItems(Boolean skipItems) {
        this.skipItems = skipItems;
    }

    public Boolean getSkipBarcodes() {
        return skipBarcodes;
    }

    public void setSkipBarcodes(Boolean skipBarcodes) {
        this.skipBarcodes = skipBarcodes;
    }
}
