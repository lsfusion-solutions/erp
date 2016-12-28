package equ.clt.handler.ukm4mysql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class UKM4MySQLConnectionString {
    String connectionString = null;
    String user = null;
    String password = null;

    //"jdbc:mysql://172.16.0.35/export_axapta?user=luxsoft&password=123456"
    UKM4MySQLConnectionString(String value, int index, UKM4MySQLSettings ukm4MySQLSettings) {
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
        } else {
            //todo: Убрать после перехода всех на чтение directoryGroupMachinery
            connectionString = ukm4MySQLSettings != null ? (index == 0 ? ukm4MySQLSettings.getImportConnectionString() : ukm4MySQLSettings.getExportConnectionString()) : null;
            user = ukm4MySQLSettings != null ? ukm4MySQLSettings.getUser() : null;
            password = ukm4MySQLSettings != null ? ukm4MySQLSettings.getPassword() : null;
        }
    }
}
