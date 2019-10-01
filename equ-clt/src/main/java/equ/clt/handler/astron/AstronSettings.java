package equ.clt.handler.astron;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class AstronSettings implements Serializable {
    private Integer timeout;
    public String groupMachineries = null;

    //если true, то выгружаем таблицы prclevel, sarea
    private boolean exportExtraTables;

    //только для pgsql
    //если true и exportExtraTables, то выгружаем таблицу sareaprc
    private boolean exportSAreaPrc;

    public AstronSettings() {
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Map<Integer, Integer> getGroupMachineryMap() {
        Map<Integer, Integer> groupMachineryMap = new HashMap<>();
        if(groupMachineries != null) {
            for (String groupMachinery : groupMachineries.split(",")) {
                String[] entry = trim(groupMachinery).split("->");
                if (entry.length == 2) {
                    Integer key = parseInt(trim(entry[0]));
                    Integer value = parseInt(trim(entry[1]));
                    if (key != null && value != null)
                        groupMachineryMap.put(key, value);
                }
            }
        }
        return groupMachineryMap;
    }

    public void setGroupMachineries(String groupMachineries) {
        this.groupMachineries = groupMachineries;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }


    public boolean isExportExtraTables() {
        return exportExtraTables;
    }

    public void setExportExtraTables(boolean exportExtraTables) {
        this.exportExtraTables = exportExtraTables;
    }

    public boolean isExportSAreaPrc() {
        return exportSAreaPrc;
    }

    public void setExportSAreaPrc(boolean exportSAreaPrc) {
        this.exportSAreaPrc = exportSAreaPrc;
    }
}