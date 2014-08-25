package equ.api.cashregister;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.util.List;

public class CashRegisterItemInfo extends ItemInfo {
    public Integer idItem;
    public String description;
    public String nameItemGroup;
    public List<ItemGroup> hierarchyItemGroup;
    public String idUOM;
    public String shortNameUOM;
    public boolean passScalesItem;
    public BigDecimal vat;
    public boolean notPromotionItem;

    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem,
                                Integer idItem, String description, String nameItemGroup, 
                                List<ItemGroup> hierarchyItemGroup, String idUOM, String shortNameUOM, 
                                boolean passScalesItem, BigDecimal vat, boolean notPromotionItem) {
        super(idBarcode, name, price, splitItem);
        this.idItem = idItem;
        this.description = description;
        this.nameItemGroup = nameItemGroup;
        this.hierarchyItemGroup = hierarchyItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
        this.passScalesItem = passScalesItem;
        this.vat = vat;
        this.notPromotionItem = notPromotionItem;
    }
}
