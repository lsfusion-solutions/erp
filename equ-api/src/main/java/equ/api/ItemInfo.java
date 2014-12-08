package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemInfo implements Serializable {
    public String idItem;
    public String idBarcode;
    public String name;
    public BigDecimal price;
    public boolean splitItem;
    public Integer pluNumber;
    public Integer daysExpiry;
    
    public ItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer pluNumber, Integer daysExpiry) {
        this.idItem = idItem;
        this.idBarcode = idBarcode;
        this.name = name;
        this.price = price;
        this.splitItem = splitItem;
        this.pluNumber = pluNumber;
        this.daysExpiry = daysExpiry;
    }
}
