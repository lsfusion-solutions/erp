package equ.clt.handler.eqs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EQSSettings implements Serializable{

    private Boolean appendBarcode;
    private String giftCardRegexp;
    private Boolean skipIdDepartmentStore;
    private String forceIdDepartmentStores;
    private List<String> forceIdDepartmentStoresList = new ArrayList<>();
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

    public List<String> getForceIdDepartmentStoresList() {
        return forceIdDepartmentStoresList;
    }

    public void setForceIdDepartmentStores(String forceIdDepartmentStores) {
        this.forceIdDepartmentStores = forceIdDepartmentStores;
        this.forceIdDepartmentStoresList.addAll(Arrays.asList(forceIdDepartmentStores.split(",\\s?")));
    }

    public int getDiscountCardThreadCount() {
        return discountCardThreadCount;
    }

    public void setDiscountCardThreadCount(int discountCardThreadCount) {
        this.discountCardThreadCount = discountCardThreadCount;
    }
}