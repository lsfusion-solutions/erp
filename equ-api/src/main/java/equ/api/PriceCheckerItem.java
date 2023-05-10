package equ.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PriceCheckerItem extends ItemInfo {
    
    public PriceCheckerItem(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                            Integer hoursExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                            String idItemGroup, String canonicalNameSkuGroup, String info, String extraInfo) {
        super(null, idItem, idBarcode, name, price, splitItem, daysExpiry, hoursExpiry, expiryDate, passScales, vat, pluNumber, flags,
                idItemGroup, canonicalNameSkuGroup, null, null, info, extraInfo);
    }
}
