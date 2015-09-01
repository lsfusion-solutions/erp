package equ.api.scales;

import equ.api.MachineryInfo;

public class ScalesInfo extends MachineryInfo {
    public String pieceCodeGroupScales;
    public String weightCodeGroupScales;
    
    public ScalesInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel, String handlerModel, String port, String directory,
                      String pieceCodeGroupScales, String weightCodeGroupScales) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory);
        this.pieceCodeGroupScales = pieceCodeGroupScales;
        this.weightCodeGroupScales = weightCodeGroupScales;
    }
}
