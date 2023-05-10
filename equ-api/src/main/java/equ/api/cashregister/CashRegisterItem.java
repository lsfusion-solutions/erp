package equ.api.cashregister;

import equ.api.ItemInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CashRegisterItem extends ItemInfo {
    public Long itemGroupObject;
    public String description;
    public String idBrand;
    public String nameBrand;
    public String idSeason;
    public String nameSeason;
    public String section;
    public String deleteSection;
    public BigDecimal minPrice;
    public String extIdItemGroup;
    public BigDecimal amountBarcode;
    public BigDecimal balance;
    public LocalDateTime balanceDate;
    public LocalDateTime restrictionToDateTime;
    public Long barcodeObject;
    public String mainBarcode;
    public Integer manufactureDays;
    public List<CashRegisterItemBatch> batchList;

    public CashRegisterItem(String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                            Integer hoursExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags,
                            String idItemGroup, String nameItemGroup, String idUOM, String shortNameUOM, String info, String extraInfo,
                            Long itemGroupObject, String description, String idBrand, String nameBrand, String idSeason, String nameSeason,
                            String section, String deleteSection, BigDecimal minPrice, String extIdItemGroup, BigDecimal amountBarcode,
                            BigDecimal balance, LocalDateTime balanceDate, LocalDateTime restrictionToDateTime, Long barcodeObject, String mainBarcode,
                            Integer manufactureDays, List<CashRegisterItemBatch> batchList) {
        super(null, idItem, idBarcode, name, price, splitItem, daysExpiry, hoursExpiry, expiryDate, passScales, vat, pluNumber, flags, idItemGroup, nameItemGroup,
                idUOM, shortNameUOM, info, extraInfo);
        this.itemGroupObject = itemGroupObject;
        this.description = description;
        this.idBrand = idBrand;
        this.nameBrand = nameBrand;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.section = section;
        this.deleteSection = deleteSection;
        this.minPrice = minPrice;
        this.extIdItemGroup = extIdItemGroup;
        this.amountBarcode = amountBarcode;
        this.balance = balance;
        this.balanceDate = balanceDate;
        this.restrictionToDateTime = restrictionToDateTime;
        this.barcodeObject = barcodeObject;
        this.mainBarcode = mainBarcode;
        this.manufactureDays = manufactureDays;
        this.batchList = batchList;
    }
}
