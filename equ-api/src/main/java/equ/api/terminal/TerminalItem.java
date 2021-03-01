package equ.api.terminal;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TerminalItem extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItem(String idItem, String barcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                        LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                        String idItemGroup, String canonicalNameSkuGroup, String info, BigDecimal quantity, String image) {
        super(null, idItem, barcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags,
                idItemGroup, canonicalNameSkuGroup, null, null, info);
        this.quantity = quantity;
        this.image = image;
    }
}
