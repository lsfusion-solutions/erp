package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TerminalBatch implements Serializable {
    public String idBatch;
    public String idBarcode;
    public String idSupplier;
    public String date;
    public String number;
    public BigDecimal price;
    public String extraField;

    public TerminalBatch(String idBatch, String idBarcode, String idSupplier, String date, String number, BigDecimal price, String extraField) {
        this.idBatch = idBatch;
        this.idBarcode = idBarcode;
        this.idSupplier = idSupplier;
        this.date = date;
        this.number = number;
        this.price = price;
        this.extraField = extraField;
    }
}
