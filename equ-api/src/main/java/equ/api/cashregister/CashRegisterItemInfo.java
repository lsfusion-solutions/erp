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
    public String idBrand;
    public String nameBrand;
    public String idSeason;
    public String nameSeason;
    public boolean passScalesItem;
    public BigDecimal vat;
    public boolean notPromotionItem;
    public Integer flags;

    public CashRegisterItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, Integer daysExpiry,
                                Integer idItem, String extIdItem, String description, String idItemGroup, String nameItemGroup, 
                                String idUOM, String shortNameUOM,  String idBrand, String nameBrand, String idSeason, String nameSeason,
                                boolean passScalesItem, BigDecimal vat, boolean notPromotionItem, Integer flags) {
        super(idBarcode, name, price, splitItem, pluNumber, daysExpiry);
        this.idItem = idItem;
        this.extIdItem = extIdItem;
        this.description = description;
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
        this.idBrand = idBrand;
        this.nameBrand = nameBrand;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.passScalesItem = passScalesItem;
        this.vat = vat;
        this.notPromotionItem = notPromotionItem;
        this.flags = flags;
    }
}
