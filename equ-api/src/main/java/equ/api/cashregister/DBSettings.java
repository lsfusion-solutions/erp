package equ.api.cashregister;

import java.io.Serializable;

public class DBSettings implements Serializable{

    public String sqlUsername;
    public String sqlPassword;
    public String sqlIp;
    public String sqlPort;
    public String sqlDBName;

    public DBSettings(String sqlUsername, String sqlPassword, String sqlIp, String sqlPort, String sqlDBName) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;
        this.sqlIp = sqlIp;
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
    }
}
