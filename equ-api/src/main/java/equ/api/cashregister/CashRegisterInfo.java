package equ.api.cashregister;

import equ.api.MachineryInfo;

import java.sql.Date;

public class CashRegisterInfo extends MachineryInfo {
    public boolean enabled;
    public Date startDate;
    public Boolean notDetailed;
    public Boolean succeeded;

    public CashRegisterInfo(boolean enabled, Integer numberGroup, Integer number, String nameModel, String handlerModel,
                            String port, String directory, Date startDate, Boolean notDetailed, Boolean succeeded) {
        super(numberGroup, number, nameModel, handlerModel, port, directory);
        this.enabled = enabled;
        this.startDate = startDate;
        this.notDetailed = notDetailed;
        this.succeeded = succeeded;
    }
}
