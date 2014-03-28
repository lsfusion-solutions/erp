package equ.api.terminal;

import equ.api.ItemInfo;

import java.math.BigDecimal;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String barcode, String name, BigDecimal price, String composition, boolean isWeightItem,
                            BigDecimal quantity, String image) {
        super(barcode, name, price, composition, isWeightItem);        
        this.quantity = quantity;
        this.image = image;
    }
}
