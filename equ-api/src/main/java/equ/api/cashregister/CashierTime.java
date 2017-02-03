package equ.api.cashregister;

import java.io.Serializable;
import java.sql.Timestamp;

public class CashierTime implements Serializable {
    public String idCashierTime;
    public String numberCashier;
    public Integer numberCashRegister;
    public Integer numberGroupCashRegister;
    public Timestamp logOnCashier;
    public Timestamp logOffCashier;
    public Boolean isZReport;

    public CashierTime(String idCashierTime, String numberCashier, Integer numberCashRegister, Integer numberGroupCashRegister,
                       Timestamp logOnCashier, Timestamp logOffCashier, Boolean isZReport) {
        this.idCashierTime = idCashierTime;
        this.numberCashier = numberCashier;
        this.numberCashRegister = numberCashRegister;
        this.numberGroupCashRegister = numberGroupCashRegister;
        this.logOnCashier = logOnCashier;
        this.logOffCashier = logOffCashier;
        this.isZReport = isZReport;
    }
}