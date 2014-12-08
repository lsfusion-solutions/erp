package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;

public class CashRegisterItemInfo extends ItemInfo {
    public Integer idItemObject;
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

    public CashRegisterItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, 
                                Integer daysExpiry, Integer idItemObject, String description, String idItemGroup, String nameItemGroup, 
                                String idUOM, String shortNameUOM,  String idBrand, String nameBrand, String idSeason, String nameSeason,
                                boolean passScalesItem, BigDecimal vat, boolean notPromotionItem, Integer flags) {
        super(idItem, idBarcode, name, price, splitItem, pluNumber, daysExpiry);
        this.idItemObject = idItemObject;
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
