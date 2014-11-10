package equ.api.cashregister;

import java.io.Serializable;

public class DBSettings implements Serializable{

    public String sqlUsername;
    public String sqlPassword;
    public String[] sqlHost;
    public String sqlPort;
    public String sqlDBName;
    public Boolean useIdItem;

    public DBSettings(String sqlUsername, String sqlPassword, String sqlHost, String sqlPort, String sqlDBName, Boolean useIdItem) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;
        this.sqlHost = sqlHost.split(",");
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
        this.useIdItem = useIdItem;
    }
}
