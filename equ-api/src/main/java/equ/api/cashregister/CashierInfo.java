package equ.api.cashregister;

import java.io.Serializable;

public class CashierInfo implements Serializable {
    public String numberCashier;
    public String nameCashier;
    public String idPosition;

    public CashierInfo(String numberCashier, String nameCashier, String idPosition) {
        this.numberCashier = numberCashier;
        this.nameCashier = nameCashier;
        this.idPosition = idPosition;
    }
}