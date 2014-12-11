package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;

public class ItemInfo implements Serializable {
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
    
    public ItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                    Date expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags) {
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
    }
}
