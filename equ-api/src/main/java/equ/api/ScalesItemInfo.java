package equ.api;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class ScalesItemInfo extends ItemInfo {
    public BigDecimal daysExpiry;
    public Integer hoursExpiry;
    public Date expirationDate;
    public Integer labelFormat;
    public Integer compositionNumber;
    public List<String> hierarchyItemGroup;
    
    public ScalesItemInfo(String idBarcode, String name, BigDecimal price, 
                          String composition, boolean isWeightItem, 
                          BigDecimal daysExpiry, Integer hoursExpiry, Date expirationDate, Integer labelFormat, 
                          Integer compositionNumber, List<String> hierarchyItemGroup) {
        super(idBarcode, name, price, composition, isWeightItem); 
        this.daysExpiry = daysExpiry;
        this.hoursExpiry = hoursExpiry;
        this.expirationDate = expirationDate;
        this.labelFormat = labelFormat;
        this.compositionNumber = compositionNumber;
        this.hierarchyItemGroup = hierarchyItemGroup;
    }
}
