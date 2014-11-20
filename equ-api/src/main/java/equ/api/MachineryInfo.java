package equ.api;

import java.io.Serializable;

public class MachineryInfo implements Serializable {
    public boolean enabled;
    public Integer numberGroup;
    public Integer number;
    public String nameModel;
    public String handlerModel;
    public String port;
    public String directory;

    public MachineryInfo(boolean enabled, Integer numberGroup, Integer number, String nameModel, String handlerModel, String port, String directory) {
        this.enabled = enabled;
        this.numberGroup = numberGroup;
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
    }
}
