package equ.api.cashregister;

import java.io.Serializable;

public class DBSettings implements Serializable{

    public String sqlUsername;
    public String sqlPassword;
    public String[] sqlHost;
    public String sqlPort;
    public String sqlDBName;
    private Boolean useIdItem;

    public DBSettings(String sqlUsername, String sqlPassword, String sqlHost, String sqlPort, String sqlDBName) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;
        this.sqlHost = sqlHost.split(",");
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
    }

    public void setUseIdItem(Boolean useIdItem) {
        this.useIdItem = useIdItem;
    }

    public Boolean getUseIdItem() {
        return useIdItem;
    }
}
