package equ.api;

import java.math.BigDecimal;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem,
                            BigDecimal quantity, String image) {
        super(idBarcode, name, price, composition, isWeightItem);        
        this.quantity = quantity;
        this.image = image;
    }
}
