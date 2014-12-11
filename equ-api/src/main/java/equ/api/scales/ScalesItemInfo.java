package equ.api.scales;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.sql.Date;

public class ScalesItemInfo extends ItemInfo {
    public Integer hoursExpiry;
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public String idItemGroup;
    public String idUOM;
    public String shortNameUOM;
    
    public ScalesItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry, 
                          Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags, Integer hoursExpiry,
                          Integer labelFormat, String description, Integer descriptionNumber, String idItemGroup,
                          String idUOM, String shortNameUOM) {
        super(idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags);
        this.hoursExpiry = hoursExpiry;
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.idItemGroup = idItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
    }
}
