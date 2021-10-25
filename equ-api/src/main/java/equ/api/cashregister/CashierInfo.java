package equ.api.cashregister;

import java.io.Serializable;

public class CashierInfo implements Serializable {
    public String numberCashier;
    public String nameCashier;
    public String idPosition;
    public String namePosition;
    public String idStock;

    public CashierInfo(String numberCashier, String nameCashier, String idPosition, String namePosition, String idStock) {
        this.numberCashier = numberCashier;
        this.nameCashier = nameCashier;
        this.idPosition = idPosition;
        this.namePosition = namePosition;
        this.idStock = idStock;
    }
}