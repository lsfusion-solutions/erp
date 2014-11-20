package equ.api.terminal;

import equ.api.MachineryInfo;

public class TerminalInfo extends MachineryInfo {
    public String idPriceListType;
    public TerminalInfo(boolean enabled, Integer numberGroup, Integer number, String nameModel, String handlerModel, String port, String directory, String idPriceListType) {
        super(enabled, numberGroup, number, nameModel, handlerModel, port, directory);
        this.idPriceListType = idPriceListType;
    }
}
