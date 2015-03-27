package equ.api.cashregister;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DBSettings implements Serializable{

    public String sqlUsername;
    public String sqlPassword;
    public Map<String, String> sqlHost;
    public String sqlPort;
    public String sqlDBName;
    private Boolean useIdItem;

    public DBSettings(String sqlUsername, String sqlPassword, String sqlHost, String sqlPort, String sqlDBName) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;       
        this.sqlHost = new HashMap<String, String>();
        if(!sqlHost.isEmpty()) {
            String[] hosts = sqlHost.split(",");
            for (String host : hosts) {
                String[] entry = host.split("->");
                this.sqlHost.put(entry[0], entry.length >= 2 ? entry[1] : entry[0]);
            }
        }
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
    }

    public void setUseIdItem(Boolean useIdItem) {
        this.useIdItem = useIdItem;
    }

    public boolean getUseIdItem() {
        return useIdItem != null && useIdItem;
    }
}
