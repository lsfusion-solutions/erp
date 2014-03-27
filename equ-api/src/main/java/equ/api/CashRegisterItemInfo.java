package equ.api;

import java.math.BigDecimal;
import java.util.List;

public class CashRegisterItemInfo extends ItemInfo {
    public String nameItemGroup;
    public Integer nppGroupMachinery;    
    
    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem, 
                                List<String> hierarchyItemGroup, String nameItemGroup, Integer nppGroupMachinery) {
        super(idBarcode, name, price, composition, isWeightItem, hierarchyItemGroup);
        this.nameItemGroup = nameItemGroup;
        this.nppGroupMachinery = nppGroupMachinery;
    }
}
