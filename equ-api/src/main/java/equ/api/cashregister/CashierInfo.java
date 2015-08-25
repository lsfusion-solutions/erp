package equ.api.cashregister;

import java.io.Serializable;

public class CashierInfo implements Serializable {
    public String numberCashier;
    public String nameCashier;

    public CashierInfo(String numberCashier, String nameCashier) {
        this.numberCashier = numberCashier;
        this.nameCashier = nameCashier;
    }
}