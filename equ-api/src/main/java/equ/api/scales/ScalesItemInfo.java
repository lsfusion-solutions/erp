package equ.api.scales;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class ScalesItemInfo extends ItemInfo {
    public Integer pluNumber;
    public Integer daysExpiry;
    public Integer hoursExpiry;
    public Date expirationDate;
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public String idItemGroup;
    
    public ScalesItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, Integer daysExpiry,
                          Integer hoursExpiry, Date expirationDate, Integer labelFormat, String description, 
                          Integer descriptionNumber, String idItemGroup) {
        super(idBarcode, name, price, splitItem);
        this.pluNumber = pluNumber;
        this.daysExpiry = daysExpiry;
        this.hoursExpiry = hoursExpiry;
        this.expirationDate = expirationDate;
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.idItemGroup = idItemGroup;
    }
}
