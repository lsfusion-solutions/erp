package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class ReceiptInstance implements Serializable {
    public BigDecimal sumDisc;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public BigDecimal sumCheck; //epay oplati
    public BigDecimal sumSalary;
    public BigDecimal sumGiftCard;
    public BigDecimal sumTotal;
    public String numberDiscountCard;

    public List<ReceiptItem> receiptSaleList;
    public List<ReceiptItem> receiptReturnList;

    public ReceiptInstance(BigDecimal sumDisc, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumCheck,
                           BigDecimal sumSalary, BigDecimal sumGiftCard,
                           BigDecimal sumTotal, String numberDiscountCard, List<ReceiptItem> receiptSaleList, List<ReceiptItem> receiptReturnList) {
        this.sumDisc = sumDisc;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumCheck = sumCheck;
        this.sumSalary = sumSalary;
        this.sumGiftCard = sumGiftCard;
        this.sumTotal = sumTotal;
        this.numberDiscountCard = numberDiscountCard;
        this.receiptSaleList = receiptSaleList;
        this.receiptReturnList = receiptReturnList;
    }
}
