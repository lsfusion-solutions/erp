package equ.clt.handler.ukm4mysql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class UKM4MySQLConnectionString {
    String connectionString = null;
    String user = null;
    String password = null;

    //"jdbc:mysql://172.16.0.35/export_axapta?user=luxsoft&password=123456"
    UKM4MySQLConnectionString(String value, int index) {
        if (value != null) {
            try {
                Pattern p = Pattern.compile("(?:([^\\?]*)\\?user=([^\\&]*)\\&password=([^;]*))?;(?:([^\\?]*)\\?user=([^\\&]*)\\&password=([^;]*))?");
                Matcher m = p.matcher(value);
                if(m.matches()) {
                    this.connectionString = m.group(1 + index * 3);
                    this.user = m.group(2 + index * 3);
                    this.password = m.group(3 + index * 3);
                } else {
                    throw new RuntimeException("Incorrect connection string: " + value);
                }
            } catch (Exception e) {
                throw new RuntimeException("Incorrect connection string: " + value, e);
            }
            //if (connectionString == null || user == null || password == null)
            //    throw new RuntimeException("Incorrect connection string: " + value);
        }
    }
}
