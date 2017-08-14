package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class ReceiptInstance implements Serializable {
    public BigDecimal sumCash;
    public BigDecimal sumCard;
    public BigDecimal sumGiftCard;
    public String cashier;
    public List<ReceiptItem> receiptList;

    public ReceiptInstance(BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard, String cashier,
                           List<ReceiptItem> receiptList) {
        this.sumCash = sumCash;
        this.sumCard = sumCard;
        this.sumGiftCard = sumGiftCard;
        this.cashier = cashier;
        this.receiptList = receiptList;

    }
}