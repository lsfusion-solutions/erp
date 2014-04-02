package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;

public class CashDocument implements Serializable {
    public String numberCashDocument;
    public Date dateCashDocument;
    public Time timeCashDocument;
    public Integer numberCashRegister;
    public BigDecimal sumCashDocument;

    public CashDocument(String numberCashDocument, Date dateCashDocument, Time timeCashDocument, Integer numberCashRegister,
                        BigDecimal sumCashDocument) {
        this.numberCashDocument = numberCashDocument;
        this.dateCashDocument = dateCashDocument;
        this.timeCashDocument = timeCashDocument;
        this.numberCashRegister = numberCashRegister;
        this.sumCashDocument = sumCashDocument;
    }
}
