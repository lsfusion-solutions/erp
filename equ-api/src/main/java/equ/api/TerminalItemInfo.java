package equ.api;

import java.math.BigDecimal;
import java.util.List;

public class TerminalItemInfo extends ItemInfo {
    public BigDecimal quantity;
    public String image;
    
    public TerminalItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem,
                            List<String> hierarchyItemGroup, BigDecimal quantity, String image) {
        super(idBarcode, name, price, composition, isWeightItem, hierarchyItemGroup);        
        this.quantity = quantity;
        this.image = image;
    }
}
