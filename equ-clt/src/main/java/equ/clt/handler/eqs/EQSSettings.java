package equ.clt.handler.eqs;

import java.io.Serializable;

public class EQSSettings implements Serializable{

    private String connectionString;
    private String user;
    private String password;

    public EQSSettings() {
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}