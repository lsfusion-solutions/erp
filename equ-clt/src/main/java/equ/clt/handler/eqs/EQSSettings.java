package equ.clt.handler.eqs;

import java.io.Serializable;

public class EQSSettings implements Serializable{

    private Boolean appendBarcode;
    private String giftCardRegexp;
    private Boolean skipIdDepartmentStore;
    private int discountCardThreadCount;

    public EQSSettings() {
    }

    public Boolean getAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(Boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public String getGiftCardRegexp() {
        return giftCardRegexp;
    }

    public void setGiftCardRegexp(String giftCardRegexp) {
        this.giftCardRegexp = giftCardRegexp;
    }

    public Boolean getSkipIdDepartmentStore() {
        return skipIdDepartmentStore;
    }

    public void setSkipIdDepartmentStore(Boolean skipIdDepartmentStore) {
        this.skipIdDepartmentStore = skipIdDepartmentStore;
    }

    public int getDiscountCardThreadCount() {
        return discountCardThreadCount;
    }

    public void setDiscountCardThreadCount(int discountCardThreadCount) {
        this.discountCardThreadCount = discountCardThreadCount;
    }
}