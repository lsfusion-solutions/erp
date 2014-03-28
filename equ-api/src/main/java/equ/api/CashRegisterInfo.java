package equ.api;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public String directory;
    public Date startDate;

    public CashRegisterInfo(Integer numberGroup, Integer number, String nameModel, String handlerModel,
                            String port, String directory, Date startDate) {
        this.numberGroup = numberGroup;
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
        this.startDate = startDate;
    }
}
