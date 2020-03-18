package equ.api.scales;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ScalesItemInfo extends ItemInfo {
    public Integer hoursExpiry;
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public BigDecimal extraPercent;
    public BigDecimal retailPrice;
    public Integer imagesCount;

    public ScalesItemInfo(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem,
                          Integer daysExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber,
                          Integer flags, String idItemGroup, String canonicalNameSkuGroup, Integer hoursExpiry,
                          Integer labelFormat, String description, Integer descriptionNumber, String idUOM,
                          String shortNameUOM, String info, BigDecimal extraPercent, BigDecimal retailPrice, Integer imagesCount) {
        super(null, idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags,
                idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, info);
        this.hoursExpiry = hoursExpiry;
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.extraPercent = extraPercent;
        this.retailPrice = retailPrice;
        this.imagesCount = imagesCount;
    }
}
