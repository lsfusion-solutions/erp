package equ.api.stoplist;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class StopListItemInfo extends ItemInfo {
    public List<Long> barcodeObjectList;

    public StopListItemInfo(Map<String, Integer> stockPluNumberMap, String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem, Integer daysExpiry,
                            LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber, Integer flags, String idItemGroup,
                            String nameItemGroup, String idUOM, String shortNameUOM, String info,
                            List<Long> barcodeObjectList) {
        super(stockPluNumberMap, idItem, idBarcode, name, price, splitItem, daysExpiry, expiryDate, passScales, vat, pluNumber, flags, idItemGroup, nameItemGroup,
                idUOM, shortNameUOM, info);
        this.barcodeObjectList = barcodeObjectList;
    }
}
