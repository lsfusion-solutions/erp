package equ.api.terminal;

import equ.api.MachineryInfo;

public class TerminalInfo extends MachineryInfo {
    public String idPriceListType;
    public TerminalInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                        String handlerModel, String port, String directory, String idPriceListType) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory);
        this.idPriceListType = idPriceListType;
    }
}
