package equ.api;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
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
    public Set<Integer> nppGroupMachinerySet;
    public Map<String, ItemInfo> stopListItemMap;
    public Map<String, List<MachineryInfo>> handlerMachineryMap;

    public StopListInfo(boolean exclude, String number, Date dateFrom, Time timeFrom, Date dateTo, Time timeTo, Set<String> idStockSet,
                        Set<Integer> nppGroupMachinerySet, Map<String, ItemInfo> stopListItemMap, Map<String, List<MachineryInfo>> handlerMachineryMap) {
        this.exclude = exclude;
        this.number = number;
        this.dateFrom = dateFrom;
        this.timeFrom = timeFrom;
        this.dateTo = dateTo;
        this.timeTo = timeTo;
        this.idStockSet = idStockSet;
        this.nppGroupMachinerySet = nppGroupMachinerySet;
        this.stopListItemMap = stopListItemMap;
        this.handlerMachineryMap = handlerMachineryMap;
    }
}
