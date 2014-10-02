package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public String directory;
    public Date startDate;
    public Boolean notDetailed;
    public Boolean succeeded;

    public CashRegisterInfo(Integer numberGroup, Integer number, String nameModel, String handlerModel,
                            String port, String directory, Date startDate, Boolean notDetailed, Boolean succeeded) {
        this.numberGroup = numberGroup;
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
        this.startDate = startDate;
        this.notDetailed = notDetailed;
        this.succeeded = succeeded;
    }
}
