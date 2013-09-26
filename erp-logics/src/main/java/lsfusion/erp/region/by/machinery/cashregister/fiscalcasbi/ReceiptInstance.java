package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class ReceiptInstance implements Serializable {
    public BigDecimal sumDisc;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public BigDecimal sumTotal;

    public List<ReceiptItem> receiptSaleList;
    public List<ReceiptItem> receiptReturnList;

    public ReceiptInstance(BigDecimal sumDisc, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumTotal,
                           List<ReceiptItem> receiptSaleList, List<ReceiptItem> receiptReturnList) {
        this.sumDisc = sumDisc;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumTotal = sumTotal;
        this.receiptSaleList = receiptSaleList;
        this.receiptReturnList = receiptReturnList;
    }
}
