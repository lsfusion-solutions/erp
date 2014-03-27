package equ.api;

import java.math.BigDecimal;
import java.util.List;

public class PriceCheckerItemInfo extends ItemInfo {
    
    public PriceCheckerItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem, List<String> hierarchyItemGroup) {
        super(idBarcode, name, price, composition, isWeightItem, hierarchyItemGroup);        
    }
}
