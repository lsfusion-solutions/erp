package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Map;

public class ItemInfo implements Serializable {
    public Map<String, Integer> stockPluNumberMap;
    public String idItem;
    public String idBarcode;
    public String name;
    public BigDecimal price;
    public boolean splitItem;
    public Integer daysExpiry;
    public Date expiryDate;
    public boolean passScalesItem;
    public BigDecimal vat;
    public Integer pluNumber;
    public Integer flags;
    public String idItemGroup;
    public String nameItemGroup;
    public String idUOM;
    public String shortNameUOM;
    
    public ItemInfo(Map<String, Integer> stockPluNumberMap, String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                    Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags, String idItemGroup,
                    String nameItemGroup, String idUOM, String shortNameUOM) {
        this.stockPluNumberMap = stockPluNumberMap;
        this.idItem = idItem;
        this.idBarcode = idBarcode;
        this.name = name;
        this.price = price;
        this.splitItem = splitItem;
        this.daysExpiry = daysExpiry;
        this.expiryDate = expiryDate;
        this.passScalesItem = passScales;
        this.vat = vat;
        this.pluNumber = pluNumber;
        this.flags = flags;
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
    }
}
