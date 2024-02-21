package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.TreeMap;

public class ReceiptInstance implements Serializable {
    public TreeMap<Integer, BigDecimal> paymentSumMap;
    public BigDecimal sumCash;
    public BigDecimal sumCard;
    public BigDecimal sumGiftCard;
    public String cashier;
    public List<ReceiptItem> receiptList;
    public String comment;

    public ReceiptInstance(TreeMap<Integer, BigDecimal> paymentSumMap, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard, String cashier,
                           List<ReceiptItem> receiptList, String comment) {
        this.paymentSumMap = paymentSumMap;
        this.sumCash = sumCash;
        this.sumCard = sumCard;
        this.sumGiftCard = sumGiftCard;
        this.cashier = cashier;
        this.receiptList = receiptList;
        this.comment = comment;
    }
}