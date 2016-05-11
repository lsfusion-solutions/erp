package equ.api.scales;

import equ.api.MachineryInfo;

public class ScalesInfo extends MachineryInfo {
    public String idStock;
    public String pieceCodeGroupScales;
    public String weightCodeGroupScales;

    public ScalesInfo(Integer numberGroup, Integer number, String handlerModel, String port, String directory, String denominationStage, String idStock) {
        super(false, false, false, numberGroup, number, null, handlerModel, port, directory, denominationStage);
        this.idStock = idStock;
    }

    public ScalesInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                      String handlerModel, String port, String directory, String denominationStage,
                      String pieceCodeGroupScales, String weightCodeGroupScales) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory, denominationStage);
        this.pieceCodeGroupScales = pieceCodeGroupScales;
        this.weightCodeGroupScales = weightCodeGroupScales;
    }
}
