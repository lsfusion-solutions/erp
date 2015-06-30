package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;
import java.sql.Date;

public class CashRegisterItemInfo extends ItemInfo {
    public Integer itemGroupObject;
    public String description;
    public String idUOM;
    public String shortNameUOM;
    public String idBrand;
    public String nameBrand;
    public String idSeason;
    public String nameSeason;
    public String idDepartmentStore;
    public String section;

    public CashRegisterItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry, 
                                Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                                String idItemGroup, String nameItemGroup, Integer itemGroupObject, String description, String idUOM,
                                String shortNameUOM, String idBrand, String nameBrand, String idSeason, String nameSeason,
                                String idDepartmentStore, String section) {
        super(idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags, idItemGroup, nameItemGroup);
        this.itemGroupObject = itemGroupObject;
        this.description = description;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
        this.idBrand = idBrand;
        this.nameBrand = nameBrand;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.idDepartmentStore = idDepartmentStore;
        this.section = section;
    }
}
