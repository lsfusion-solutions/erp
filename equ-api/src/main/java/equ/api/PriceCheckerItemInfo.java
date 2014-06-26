package equ.api;

import java.math.BigDecimal;

public class PriceCheckerItemInfo extends ItemInfo {
    
    public PriceCheckerItemInfo(String idBarcode, String name, BigDecimal price, boolean isWeightItem) {
        super(idBarcode, name, price, isWeightItem);
    }
}
