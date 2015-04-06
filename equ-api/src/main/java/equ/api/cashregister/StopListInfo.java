package equ.api.cashregister;

import equ.api.ItemInfo;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;
import java.util.Map;
import java.util.Set;

public class StopListInfo implements Serializable {
    public boolean exclude;
    public String number;
    public Date dateFrom;
    public Time timeFrom;
    public Date dateTo;
    public Time timeTo;
    public Set<String> idStockSet;
    public Map<String, ItemInfo> stopListItemMap;
    public Map<String, Set<String>> handlerDirectoryMap;

    public StopListInfo(boolean exclude, String number, Date dateFrom, Time timeFrom, Date dateTo, Time timeTo, Set<String> idStockSet,
                        Map<String, ItemInfo> stopListItemMap, Map<String, Set<String>> handlerDirectoryMap) {
        this.exclude = exclude;
        this.number = number;
        this.dateFrom = dateFrom;
        this.timeFrom = timeFrom;
        this.dateTo = dateTo;
        this.timeTo = timeTo;
        this.idStockSet = idStockSet;
        this.stopListItemMap = stopListItemMap;
        this.handlerDirectoryMap = handlerDirectoryMap;
    }
}
