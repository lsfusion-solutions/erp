package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public Date startDate;
    public Integer overDepartNumber;
    public Boolean notDetailed;
    public Boolean succeeded;
    public String pieceCodeGroupCashRegister;
    public String weightCodeGroupCashRegister;

    public CashRegisterInfo(boolean enabled, Integer numberGroup, Integer number, String nameModel, String handlerModel,
                            String port, String directory, Date startDate, Integer overDepartNumber, Boolean notDetailed,
                            Boolean succeeded, String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister) {
        super(enabled, numberGroup, number, nameModel, handlerModel, port, directory);
        this.startDate = startDate;
        this.overDepartNumber = overDepartNumber;
        this.notDetailed = notDetailed;
        this.succeeded = succeeded;
        this.pieceCodeGroupCashRegister = pieceCodeGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
    }
}
