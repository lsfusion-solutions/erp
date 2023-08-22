package equ.api.stoplist;

import equ.api.ItemInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class StopListItem extends ItemInfo {
    public List<Long> barcodeObjectList;
    public Integer innerIdUOM;

    public StopListItem(Map<String, Integer> stockPluNumberMap, String idItem, String idBarcode, String name, BigDecimal price, boolean splitItem,
                        Integer daysExpiry, Integer hoursExpiry, LocalDate expiryDate, boolean passScales, BigDecimal vat, Integer pluNumber,
                        Integer flags, String idItemGroup, String nameItemGroup, String idUOM, String shortNameUOM, String info, String extraInfo,
                        List<Long> barcodeObjectList, Integer innerIdUOM) {
        super(stockPluNumberMap, idItem, idBarcode, name, price, splitItem, daysExpiry, hoursExpiry, expiryDate, passScales, vat, pluNumber, flags, idItemGroup, nameItemGroup,
                idUOM, shortNameUOM, info, extraInfo);
        this.barcodeObjectList = barcodeObjectList;
        this.innerIdUOM = innerIdUOM;
    }
}
