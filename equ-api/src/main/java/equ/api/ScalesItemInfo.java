package equ.api;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class ScalesItemInfo extends ItemInfo {
    public BigDecimal daysExpiry;
    public Integer hoursExpiry;
    public Date expirationDate;
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public List<String> hierarchyItemGroup;
    
    public ScalesItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem, BigDecimal daysExpiry,
                          Integer hoursExpiry, Date expirationDate, Integer labelFormat, String description, 
                          Integer descriptionNumber, List<String> hierarchyItemGroup) {
        super(idBarcode, name, price, splitItem);
        this.daysExpiry = daysExpiry;
        this.hoursExpiry = hoursExpiry;
        this.expirationDate = expirationDate;
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.hierarchyItemGroup = hierarchyItemGroup;
    }
}
