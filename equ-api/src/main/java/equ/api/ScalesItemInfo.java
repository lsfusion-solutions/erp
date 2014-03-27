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
    
    public ScalesItemInfo(String idBarcode, String name, BigDecimal price, 
                          String composition, boolean isWeightItem, List<String> hierarchyItemGroup,
                          BigDecimal daysExpiry, Integer hoursExpiry, Date expirationDate, Integer labelFormat, 
                          Integer compositionNumber) {
        super(idBarcode, name, price, composition, isWeightItem, hierarchyItemGroup); 
        this.daysExpiry = daysExpiry;
        this.hoursExpiry = hoursExpiry;
        this.expirationDate = expirationDate;
        this.labelFormat = labelFormat;
        this.compositionNumber = compositionNumber;
    }
}
