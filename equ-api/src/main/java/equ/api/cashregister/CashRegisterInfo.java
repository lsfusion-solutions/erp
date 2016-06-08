package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public Date startDate;
    public Integer overDepartNumber;
    public String idDepartmentStore;
    public boolean notDetailed;
    public boolean disableSales;
    public String pieceCodeGroupCashRegister;
    public String weightCodeGroupCashRegister;
    public String section;

    public CashRegisterInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory,
                            String denominationStage, String section, Integer overDepartNumber) {
        this(numberGroup, number, null, handlerModel, port, directory, denominationStage, overDepartNumber, null, false, null, null, section);
    }

    public CashRegisterInfo(Integer numberGroup, Integer number, String nameModel, String handlerModel, String port, String directory,
                            String denominationStage, Integer overDepartNumber, String idDepartmentStore, boolean disableSales,
                            String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister, String section) {
        this(true, false, false, numberGroup, number, nameModel, handlerModel, port, directory, denominationStage, null, overDepartNumber, idDepartmentStore,
                 false,  disableSales,  pieceCodeGroupCashRegister, weightCodeGroupCashRegister, section);
    }

    public CashRegisterInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                            String handlerModel, String port, String directory, String denominationStage, Date startDate,
                            Integer overDepartNumber, String idDepartmentStore, Boolean notDetailed, boolean disableSales,
                            String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister, String section) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory, denominationStage);
        this.startDate = startDate;
        this.overDepartNumber = overDepartNumber;
        this.idDepartmentStore = idDepartmentStore;
        this.notDetailed = notDetailed;
        this.disableSales = disableSales;
        this.pieceCodeGroupCashRegister = pieceCodeGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
        this.section = section;
    }
}
