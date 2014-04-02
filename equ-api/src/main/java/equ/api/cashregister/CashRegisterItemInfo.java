package equ.api.cashregister;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.util.List;

public class CashRegisterItemInfo extends ItemInfo {
    public String composition;
    public String nameItemGroup;
    public List<String> hierarchyItemGroup;
    
    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, boolean isWeightItem,
                                String composition, String nameItemGroup, List<String> hierarchyItemGroup) {
        super(idBarcode, name, price, isWeightItem);
        this.composition = composition;
        this.nameItemGroup = nameItemGroup;
        this.hierarchyItemGroup = hierarchyItemGroup;
    }
}
