package equ.clt.handler.astron;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AstronConnectionString {
    String connectionString = null;
    String user = null;
    String password = null;
    boolean pgsql = false;

    //"jdbc:sqlserver://192.168.42.42;databaseName=Tranzit_DB3?user=sa&password=123456"
    public AstronConnectionString(String value) {
        if (value != null) {
            try {
                Pattern p = Pattern.compile("(jdbc:(sqlserver|postgresql)[^?]*)\\?user=([^&]*)&password=([^;]*)");
                Matcher m = p.matcher(value);
                if(m.matches()) {
                    this.connectionString = m.group(1);
                    this.pgsql = m.group(2).equals("postgresql");
                    this.user = m.group(3);
                    this.password = m.group(4);
                } else {
                    throw new RuntimeException("Incorrect connection string: " + value);
                }
            } catch (Exception e) {
                throw new RuntimeException("Incorrect connection string: " + value, e);
            }
        }
    }

    public boolean newScheme() {
        return !pgsql; //используем новую схему только для mssql
    }

    public boolean versionalScheme(boolean isVersionalScheme) {
        return isVersionalScheme && !pgsql; //схема с версионностью пока только для mssql
    }
}
