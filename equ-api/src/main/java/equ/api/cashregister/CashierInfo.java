package equ.api.cashregister;

import java.io.Serializable;

public class CashierInfo implements Serializable {
    public String idCashier;
    public String nameCashier;
    public String passwordCashier;
    public String idPosition;
    public String namePosition;
    public String idStock;

    public CashierInfo(String idCashier, String nameCashier, String passwordCashier, String idPosition, String namePosition, String idStock) {
        this.idCashier = idCashier;
        this.nameCashier = nameCashier;
        this.passwordCashier = passwordCashier;
        this.idPosition = idPosition;
        this.namePosition = namePosition;
        this.idStock = idStock;
    }
}