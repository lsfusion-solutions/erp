package lsfusion.erp.region.by.integration.topby;

import java.math.BigDecimal;

public class InputDocumentDetail {
    String id;
    String barcode;
    BigDecimal quantity;
    BigDecimal price;
    BigDecimal vat;
    BigDecimal vatSum;
    BigDecimal netWeight;

    public InputDocumentDetail(String id, String barcode, BigDecimal quantity, BigDecimal price, BigDecimal vat, BigDecimal vatSum, BigDecimal netWeight) {
        this.id = id;
        this.barcode = barcode;
        this.quantity = quantity;
        this.price = price;
        this.vat = vat;
        this.vatSum = vatSum;
        this.netWeight = netWeight;
    }
}
