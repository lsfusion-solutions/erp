package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public Date startDate;
    public Integer overDepartNumber;
    public boolean notDetailed;
    public boolean disableSales;
    public String pieceCodeGroupCashRegister;
    public String weightCodeGroupCashRegister;

    public CashRegisterInfo(Integer numberGroup, Integer number, String nameModel, String handlerModel, String port, String directory, Integer overDepartNumber,
                            boolean disableSales, String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister) {
        this(true, false, false, numberGroup, number, nameModel, handlerModel, port, directory, null, overDepartNumber,
                 false,  disableSales,  pieceCodeGroupCashRegister, weightCodeGroupCashRegister);
    }

    public CashRegisterInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                            String handlerModel, String port, String directory, Date startDate, Integer overDepartNumber,
                            Boolean notDetailed, boolean disableSales, String pieceCodeGroupCashRegister,
                            String weightCodeGroupCashRegister) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory);
        this.startDate = startDate;
        this.overDepartNumber = overDepartNumber;
        this.notDetailed = notDetailed;
        this.disableSales = disableSales;
        this.pieceCodeGroupCashRegister = pieceCodeGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
    }
}
