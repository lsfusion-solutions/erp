package equ.api.cashregister;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.util.List;

public class CashRegisterItemInfo extends ItemInfo {
    public String composition;
    public String nameItemGroup;
    public List<ItemGroup> hierarchyItemGroup;
    public String idUOM;
    public String shortNameUOM;
    
    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, boolean isWeightItem,
                                boolean passScalesItem, String composition, String nameItemGroup,
                                List<ItemGroup> hierarchyItemGroup, String idUOM, String shortNameUOM) {
        super(idBarcode, name, price, isWeightItem, passScalesItem);
        this.composition = composition;
        this.nameItemGroup = nameItemGroup;
        this.hierarchyItemGroup = hierarchyItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
    }
}
