package equ.api;

import java.io.Serializable;
import java.sql.Timestamp;

public class SoftCheckInvoice implements Serializable {

    public String idCustomerStock;
    public Timestamp dateTime;

    public SoftCheckInvoice(String idCustomerStock, Timestamp dateTime) {
        this.idCustomerStock = idCustomerStock;
        this.dateTime = dateTime;
    }
}
