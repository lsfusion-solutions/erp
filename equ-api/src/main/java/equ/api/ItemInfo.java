package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemInfo implements Serializable {
    public String idBarcode;
    public String name;
    public BigDecimal price;
    public boolean splitItem;
    
    public ItemInfo(String idBarcode, String name, BigDecimal price, boolean splitItem) {
        this.idBarcode = idBarcode;
        this.name = name;
        this.price = price;
        this.splitItem = splitItem;
    }
}
