package equ.api.scales;

import equ.api.MachineryInfo;

public class ScalesInfo extends MachineryInfo {
    public String idStock;
    public String pieceCodeGroupScales;
    public String weightCodeGroupScales;

    public ScalesInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory, String idStock) {
        super(false, false, false, numberGroup, number, handlerModel, port, directory);
        this.idStock = idStock;
    }

    public ScalesInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number,
                      String handlerModel, String port, String directory,
                      String pieceCodeGroupScales, String weightCodeGroupScales) {
        super(enabled, cleared, succeeded, numberGroup, number, handlerModel, port, directory);
        this.pieceCodeGroupScales = pieceCodeGroupScales;
        this.weightCodeGroupScales = weightCodeGroupScales;
    }
}
