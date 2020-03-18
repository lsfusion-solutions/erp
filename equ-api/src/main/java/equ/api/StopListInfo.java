package equ.api;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

public class StopListInfo implements Serializable {
    public boolean exclude;
    public String number;
    public LocalDate dateFrom;
    public LocalTime timeFrom;
    public LocalDate dateTo;
    public LocalTime timeTo;
    public Set<String> idStockSet;
    public Map<Integer, Set<String>> inGroupMachineryItemMap;
    public Map<String, ItemInfo> stopListItemMap;
    public Map<String, Set<MachineryInfo>> handlerMachineryMap;

    public StopListInfo(boolean exclude, String number, LocalDate dateFrom, LocalTime timeFrom, LocalDate dateTo, LocalTime timeTo, Set<String> idStockSet,
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
