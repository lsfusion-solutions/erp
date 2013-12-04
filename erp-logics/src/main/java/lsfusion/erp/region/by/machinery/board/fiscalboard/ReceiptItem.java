package lsfusion.erp.region.by.machinery.board.fiscalboard;

import java.io.Serializable;

public class ReceiptItem implements Serializable {
    public long price;
    public double quantity;
    public String name;
    public long sumPos;

    public ReceiptItem(long price, double quantity, String name, long sumPos) {
        this.price = price;
        this.quantity = quantity;
        this.name = name;
        this.sumPos = sumPos;
    }
}
