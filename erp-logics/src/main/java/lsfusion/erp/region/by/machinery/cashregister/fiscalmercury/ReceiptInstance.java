package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class ReceiptInstance implements Serializable {
    public BigDecimal sumCash;
    public BigDecimal sumCard;
    public BigDecimal sumGiftCard;
    public List<String> giftCardNumbers;
    public String cashierName;
    public String holderDiscountCard;
    public String numberDiscountCard;
    public List<ReceiptItem> receiptList;

    public ReceiptInstance(BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard, List<String> giftCardNumbers,
                           String cashierName, String holderDiscountCard, String numberDiscountCard,
                           List<ReceiptItem> receiptList) {
        this.sumCash = sumCash;
        this.sumCard = sumCard;
        this.sumGiftCard = sumGiftCard;
        this.giftCardNumbers = giftCardNumbers;
        this.cashierName = cashierName;
        this.holderDiscountCard = holderDiscountCard;
        this.numberDiscountCard = numberDiscountCard;
        this.receiptList = receiptList;

    }
}
