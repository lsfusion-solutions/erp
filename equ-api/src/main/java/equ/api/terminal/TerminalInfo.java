package equ.api.terminal;

import equ.api.MachineryInfo;

public class TerminalInfo extends MachineryInfo {
    public String idPriceListType;
    public String directory;
    public TerminalInfo(Integer number, String nameModel, String handlerModel, String port, String idPriceListType, String directory) {
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.idPriceListType = idPriceListType;
        this.directory = directory;
    }
}
