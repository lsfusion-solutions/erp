package equ.api.terminal;

import equ.api.ItemInfo;

import java.math.BigDecimal;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String idItem, String barcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, 
                            Integer daysExpiry, BigDecimal quantity, String image) {
        super(idItem, barcode, name, price, splitItem, pluNumber, daysExpiry);
        this.quantity = quantity;
        this.image = image;
    }
}
