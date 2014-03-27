package equ.api;

import java.math.BigDecimal;

public class PriceCheckerItemInfo extends ItemInfo {
    
    public PriceCheckerItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem) {
        super(idBarcode, name, price, composition, isWeightItem);        
    }
}
