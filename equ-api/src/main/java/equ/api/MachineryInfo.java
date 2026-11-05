package equ.api;

import java.io.Serializable;

public class MachineryInfo implements Serializable {
    public boolean enabled;
    public boolean cleared;
    public boolean succeeded;
    public Integer numberGroup;
    public Integer number;
    public String handlerModel;
    public String port;
    public String directory;

    public MachineryInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number,
                         String handlerModel, String port, String directory) {
        this.enabled = enabled;
        this.cleared = cleared;
        this.succeeded = succeeded;
        this.numberGroup = numberGroup;
        this.number = number;
        this.handlerModel = handlerModel;
        this.port = port;
        this.directory = directory;
    }
}
