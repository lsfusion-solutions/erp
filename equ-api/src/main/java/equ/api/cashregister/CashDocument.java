package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;

public class CashDocument implements Serializable {
    public String numberCashDocument;
    public Date dateCashDocument;
    public Time timeCashDocument;
    public Integer nppMachinery;
    public BigDecimal sumCashDocument;

    public CashDocument(String numberCashDocument, Date dateCashDocument, Time timeCashDocument, Integer nppMachinery,
                        BigDecimal sumCashDocument) {
        this.numberCashDocument = numberCashDocument;
        this.dateCashDocument = dateCashDocument;
        this.timeCashDocument = timeCashDocument;
        this.nppMachinery = nppMachinery;
        this.sumCashDocument = sumCashDocument;
    }
}
