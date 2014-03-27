package equ.api;

import java.math.BigDecimal;
import java.util.List;

public class CashRegisterItemInfo extends ItemInfo {
    public String nameItemGroup;
    public List<String> hierarchyItemGroup;
    public Integer nppGroupMachinery;
    
    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, String composition, boolean isWeightItem, 
                                String nameItemGroup, List<String> hierarchyItemGroup, Integer nppGroupMachinery) {
        super(idBarcode, name, price, composition, isWeightItem);
        this.nameItemGroup = nameItemGroup;
        this.hierarchyItemGroup = hierarchyItemGroup;
        this.nppGroupMachinery = nppGroupMachinery;
    }
}
