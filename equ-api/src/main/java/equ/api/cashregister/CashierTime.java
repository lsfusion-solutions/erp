package equ.api.cashregister;

import java.io.Serializable;
import java.time.LocalDateTime;

public class CashierTime implements Serializable {
    public String idCashierTime;
    public String numberCashier;
    public Integer numberCashRegister;
    public Integer numberGroupCashRegister;
    public LocalDateTime logOnCashier;
    public LocalDateTime logOffCashier;
    public Boolean isZReport;

    public CashierTime(String idCashierTime, String numberCashier, Integer numberCashRegister, Integer numberGroupCashRegister,
                       LocalDateTime logOnCashier, LocalDateTime logOffCashier, Boolean isZReport) {
        this.idCashierTime = idCashierTime;
        this.numberCashier = numberCashier;
        this.numberCashRegister = numberCashRegister;
        this.numberGroupCashRegister = numberGroupCashRegister;
        this.logOnCashier = logOnCashier;
        this.logOffCashier = logOffCashier;
        this.isZReport = isZReport;
    }
}