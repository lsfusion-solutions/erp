package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.time.LocalDate;

public class CashRegisterInfo extends MachineryInfo {
    public LocalDate startDate;
    public Integer overDepartNumber;
    public String idDepartmentStore;
    public boolean notDetailed;
    public boolean disableSales;
    public String pieceCodeGroupCashRegister;
    public String weightCodeGroupCashRegister;
    public String section;
    public LocalDate documentsClosedDate;
    public Integer priority;

    public CashRegisterInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory,
                            String section, Integer overDepartNumber) {
        this(numberGroup, number, handlerModel, port, directory, null, overDepartNumber, null, false, null, null, section, null, null);
    }

    public CashRegisterInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory,
                            Integer overDepartNumber, String idDepartmentStore, boolean disableSales,
                            String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister, String section) {
        this(true, false, false, numberGroup, number, handlerModel, port, directory, null, overDepartNumber, idDepartmentStore,
                false,  disableSales,  pieceCodeGroupCashRegister, weightCodeGroupCashRegister, section, null, null);
    }

    public CashRegisterInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory,
                            LocalDate startDate, Integer overDepartNumber, String idDepartmentStore, boolean disableSales,
                            String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister, String section,
                            LocalDate documentsClosedDate, Integer priority) {
        this(true, false, false, numberGroup, number, handlerModel, port, directory, startDate, overDepartNumber, idDepartmentStore,
                 false,  disableSales,  pieceCodeGroupCashRegister, weightCodeGroupCashRegister, section, documentsClosedDate, priority);
    }

    public CashRegisterInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number,
                            String handlerModel, String port, String directory, LocalDate startDate,
                            Integer overDepartNumber, String idDepartmentStore, Boolean notDetailed, boolean disableSales,
                            String pieceCodeGroupCashRegister, String weightCodeGroupCashRegister, String section,
                            LocalDate documentsClosedDate, Integer priority) {
        super(enabled, cleared, succeeded, numberGroup, number, handlerModel, port, directory);
        this.startDate = startDate;
        this.overDepartNumber = overDepartNumber;
        this.idDepartmentStore = idDepartmentStore;
        this.notDetailed = notDetailed;
        this.disableSales = disableSales;
        this.pieceCodeGroupCashRegister = pieceCodeGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
        this.section = section;
        this.documentsClosedDate = documentsClosedDate;
        this.priority = priority;
    }
}
