package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;
    private boolean deleteDiscountCardsBeforeAdd;
    private boolean appendBarcode;
    private String giftCardRegexp;

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isDeleteDiscountCardsBeforeAdd() {
        return deleteDiscountCardsBeforeAdd;
    }

    public void setDeleteDiscountCardsBeforeAdd(boolean deleteDiscountCardsBeforeAdd) {
        this.deleteDiscountCardsBeforeAdd = deleteDiscountCardsBeforeAdd;
    }

    public boolean isAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public String getGiftCardRegexp() {
        return giftCardRegexp;
    }

    public void setGiftCardRegexp(String giftCardRegexp) {
        this.giftCardRegexp = giftCardRegexp;
    }
}