package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;
import java.sql.Date;

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
    public boolean notPromotionItem;

    public CashRegisterItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry, 
                                Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags, Integer idItemObject,
                                String description, String idItemGroup, String nameItemGroup, String idUOM, String shortNameUOM,  
                                String idBrand, String nameBrand, String idSeason, String nameSeason, boolean notPromotionItem) {
        super(idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags);
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
        this.notPromotionItem = notPromotionItem;
    }
}
