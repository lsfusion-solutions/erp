package equ.api;

import java.io.Serializable;

public class MachineryInfo implements Serializable {
    public boolean enabled;
    public boolean cleared;
    public boolean succeeded;
    public Integer numberGroup;
    public Integer number;
    public String nameModel;
    public String handlerModel;
    public String port;
    public String directory;
    public String denominationStage;

    public MachineryInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                         String handlerModel, String port, String directory) {
        this(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, directory, null);
    }

    public MachineryInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number, String nameModel,
                         String handlerModel, String port, String directory, String denominationStage) {
        this.enabled = enabled;
        this.cleared = cleared;
        this.succeeded = succeeded;
        this.numberGroup = numberGroup;
        this.number = number;
        this.nameModel = nameModel;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
        this.denominationStage = denominationStage;
    }
}
