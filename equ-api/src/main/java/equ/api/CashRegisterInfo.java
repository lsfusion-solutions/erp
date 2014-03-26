package equ.api;

public class CashRegisterInfo extends MachineryInfo {
    public String directory;

    public CashRegisterInfo(Integer numberGroup, Integer number, String nameModel, String handlerModel,
                            String port, String directory) {
        this.numberGroup = numberGroup;
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
    }
}
