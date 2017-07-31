package equ.clt.handler.eqs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class EQSConnectionString {
    String connectionString = null;
    String user = null;
    String password = null;

    //"jdbc:mysql://172.16.0.35/export_axapta?user=luxsoft&password=123456"
    EQSConnectionString(String value) {
        if (value != null) {
            try {
                Pattern p = Pattern.compile("([^\\?]*)\\?user=([^\\&]*)(?:\\&password=(.*))?");
                Matcher m = p.matcher(value);
                if(m.matches()) {
                    //https://stackoverflow.com/questions/26307760/mysql-and-jdbc-with-rewritebatchedstatements-true
                    this.connectionString = m.group(1) + "?rewriteBatchedStatements=true";
                    this.user = m.group(2);
                    this.password = m.groupCount() >= 3 ? m.group(3) : null;
                } else {
                    throw new RuntimeException("Incorrect connection string: " + value);
                }
            } catch (Exception e) {
                throw new RuntimeException("Incorrect connection string: " + value, e);
            }
        }
    }
}
