package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public abstract class ItemInfo implements Serializable {
    public Map<String, Integer> stockPluNumberMap;
    public String idItem;
    public String idBarcode;
    public String name;
    public BigDecimal price;
    public boolean splitItem;
    public Integer daysExpiry;
    public Integer hoursExpiry;
    public LocalDate expiryDate;
    public boolean passScalesItem;
    public BigDecimal vat;
    public Integer pluNumber;
    public Integer flags;
    public String idItemGroup;
    public String nameItemGroup;
    public String idUOM;
    public String shortNameUOM;
    public String info;
    
    public ItemInfo(Map<String, Integer> stockPluNumberMap, String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem,
                    Integer daysExpiry, Integer hoursExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber,
                    Integer flags, String idItemGroup, String nameItemGroup, String idUOM, String shortNameUOM, String info) {
        this.stockPluNumberMap = stockPluNumberMap;
        this.idItem = idItem;
        this.idBarcode = idBarcode;
        this.name = name;
        this.price = price;
        this.splitItem = splitItem;
        this.daysExpiry = daysExpiry;
        this.hoursExpiry = hoursExpiry;
        this.expiryDate = expiryDate;
        this.passScalesItem = passScales;
        this.vat = vat;
        this.pluNumber = pluNumber;
        this.flags = flags;
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idUOM = idUOM;
        this.shortNameUOM = shortNameUOM;
        this.info = info;
    }
}
