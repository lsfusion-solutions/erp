package equ.api.terminal;

import equ.api.ItemInfo;

import java.math.BigDecimal;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String barcode, String name, BigDecimal price, boolean isWeightItem, boolean passScalesItem,
                            BigDecimal quantity, String image) {
        super(barcode, name, price, isWeightItem, passScalesItem);
        this.quantity = quantity;
        this.image = image;
    }
}
