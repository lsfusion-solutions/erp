package equ.clt.handler.artix;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;
    private boolean appendBarcode;
    private String giftCardRegexp;
    private String discountCardNames;
    private Map<String, String> discountCardNamesMap = new HashMap();

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
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

    public void setDiscountCardNames(String discountCardNames) {
        this.discountCardNames = discountCardNames;
        this.discountCardNamesMap = new HashMap<>();
        if(!discountCardNames.isEmpty()) {
            String[] entries = discountCardNames.split(",\\s?");
            for (String entry : entries) {
                String[] percentType = entry.split("->");
                if(percentType.length == 2) {
                    try {
                        this.discountCardNamesMap.put(percentType[0], percentType[1]);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public Map<String, String> getDiscountCardNamesMap() {
        return discountCardNamesMap;
    }
}