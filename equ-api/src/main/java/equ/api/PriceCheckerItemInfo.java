package equ.api;

import java.math.BigDecimal;

public class PriceCheckerItemInfo extends ItemInfo {
    
    public PriceCheckerItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, Integer daysExpiry) {
        super(idBarcode, name, price, splitItem, pluNumber, daysExpiry);
    }
}
