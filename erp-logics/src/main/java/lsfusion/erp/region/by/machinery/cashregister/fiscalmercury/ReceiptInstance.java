package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReceiptInstance implements Serializable {
    int payType;
    public Double sumDisc;
    public Double sumCard;
    public Double sumCash;
    public Double sumTotal;
    public String cashierName;
    public String clientName;
    public Double clientSum;
    public Number clientDiscount; //скидка без учета сертификатов
    public List<ReceiptItem> receiptList;

    public ReceiptInstance(int payType) {
        this.payType = payType;
        receiptList = new ArrayList<ReceiptItem>();
    }

    public void addReceipt(ReceiptItem item) {
        receiptList.add(item);
    }
}
