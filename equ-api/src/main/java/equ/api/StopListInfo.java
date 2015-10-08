package equ.api;

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
    public Map<Integer, Set<String>> inGroupMachineryItemMap;
    public Map<String, ItemInfo> stopListItemMap;
    public Map<String, Set<MachineryInfo>> handlerMachineryMap;

    public StopListInfo(boolean exclude, String number, Date dateFrom, Time timeFrom, Date dateTo, Time timeTo, Set<String> idStockSet,
                        Map<Integer, Set<String>> inGroupMachineryItemMap, Map<String, ItemInfo> stopListItemMap,
                        Map<String, Set<MachineryInfo>> handlerMachineryMap) {
        this.exclude = exclude;
        this.number = number;
        this.dateFrom = dateFrom;
        this.timeFrom = timeFrom;
        this.dateTo = dateTo;
        this.timeTo = timeTo;
        this.idStockSet = idStockSet;
        this.inGroupMachineryItemMap = inGroupMachineryItemMap;
        this.stopListItemMap = stopListItemMap;
        this.handlerMachineryMap = handlerMachineryMap;
    }
}
