package equ.api.scales;

import equ.api.ItemInfo;
import lsfusion.base.file.RawFileData;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ScalesItem extends ItemInfo {
    public Integer labelFormat;
    public String description;
    public Integer descriptionNumber;
    public BigDecimal extraPercent;
    public BigDecimal retailPrice;
    public Integer imagesCount;
    public RawFileData groupImage;
    public RawFileData itemImage;

    public ScalesItem(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem,
                      Integer daysExpiry, Integer hoursExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber,
                      Integer flags, String idItemGroup, String canonicalNameSkuGroup,
                      Integer labelFormat, String description, Integer descriptionNumber, String idUOM,
                      String shortNameUOM, String info, BigDecimal extraPercent, BigDecimal retailPrice, Integer imagesCount,
                      RawFileData groupImage, RawFileData itemImage) {
        super(null, idItem, idBarcode, name, price, splitItem, daysExpiry, hoursExpiry, expiryDate, passScales, vat, pluNumber, flags,
                idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, info);
        this.labelFormat = labelFormat;
        this.description = description;
        this.descriptionNumber = descriptionNumber;
        this.extraPercent = extraPercent;
        this.retailPrice = retailPrice;
        this.imagesCount = imagesCount;
        this.groupImage = groupImage;
        this.itemImage = itemImage;
    }
}
