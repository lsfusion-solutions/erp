package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public Date startDate;
    public Integer overDepartNumber;
    public Boolean notDetailed;
    public Boolean succeeded;
    public boolean disableSales;
    public String pieceCodeGroupCashRegister;
    public String weightCodeGroupCashRegister;

    public CashRegisterInfo(boolean enabled, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                            String handlerModel, String port, String directory, Date startDate, Integer overDepartNumber,
                            Boolean notDetailed, boolean disableSales, String pieceCodeGroupCashRegister,
                            String weightCodeGroupCashRegister) {
        super(enabled, succeeded, numberGroup, number, nameModel, handlerModel, port, directory);
        this.startDate = startDate;
        this.overDepartNumber = overDepartNumber;
        this.notDetailed = notDetailed;
        this.disableSales = disableSales;
        this.pieceCodeGroupCashRegister = pieceCodeGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
    }
}
