package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;

public class CashRegisterItemInfo extends ItemInfo {
    public Integer idItem;
    public String extIdItem;
    public String description;
    public String idItemGroup;
    public String nameItemGroup;
    public String idUOM;
    public String shortNameUOM;
    public boolean passScalesItem;
    public BigDecimal vat;
    public boolean notPromotionItem;

    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem,
                                Integer idItem, String extIdItem, String description, String idItemGroup, String nameItemGroup, 
                                String idUOM, String shortNameUOM,  boolean passScalesItem, BigDecimal vat, 
                                boolean notPromotionItem) {
        super(idBarcode, name, price, splitItem);
        this.idItem = idItem;
        this.extIdItem = extIdItem;
        this.description = description;
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
        this.passScalesItem = passScalesItem;
        this.vat = vat;
        this.notPromotionItem = notPromotionItem;
    }
}
