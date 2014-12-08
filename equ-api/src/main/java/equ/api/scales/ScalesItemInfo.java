package equ.api.scales;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.util.Date;

public class ScalesItemInfo extends ItemInfo {
    public Integer hoursExpiry;
    public Date expiryDate;
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public String idItemGroup;
    
    public ScalesItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, 
                          Integer daysExpiry, Integer hoursExpiry, Date expiryDate, Integer labelFormat, String description, 
                          Integer descriptionNumber, String idItemGroup) {
        super(idItem, idBarcode, name, price, splitItem, pluNumber, daysExpiry);
        this.hoursExpiry = hoursExpiry;
        this.expiryDate = expiryDate;
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.idItemGroup = idItemGroup;
    }
}
