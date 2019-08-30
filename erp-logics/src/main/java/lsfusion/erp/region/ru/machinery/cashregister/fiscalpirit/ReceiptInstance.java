package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class ReceiptInstance implements Serializable {
    public BigDecimal sumDisc;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public BigDecimal sumGiftCard;
    public BigDecimal sumTotal;
    public String numberDiscountCard;

    public List<ReceiptItem> receiptSaleList;
    public List<ReceiptItem> receiptReturnList;

    public ReceiptInstance(BigDecimal sumDisc, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumGiftCard,
                           BigDecimal sumTotal, String numberDiscountCard, List<ReceiptItem> receiptSaleList, List<ReceiptItem> receiptReturnList) {
        this.sumDisc = sumDisc;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumGiftCard = sumGiftCard;
        this.sumTotal = sumTotal;
        this.numberDiscountCard = numberDiscountCard;
        this.receiptSaleList = receiptSaleList;
        this.receiptReturnList = receiptReturnList;
    }
}