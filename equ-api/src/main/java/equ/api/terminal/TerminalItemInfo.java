package equ.api.terminal;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.sql.Date;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String idItem, String barcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry, 
                            Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                            String idItemGroup, String canonicalNameSkuGroup, BigDecimal quantity, String image) {
        super(null, idItem, barcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags,
                idItemGroup, canonicalNameSkuGroup, null, null);
        this.quantity = quantity;
        this.image = image;
    }
}
