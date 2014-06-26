package equ.api.cashregister;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StopListInfo implements Serializable {
    public String number;
    public Date dateFrom;
    public Time timeFrom;
    public Date dateTo;
    public Time timeTo;
    public Set<String> idStockSet;
    public List<String> stopListItemList;
    public Map<String, Set<String>> handlerDirectoryMap;

    public StopListInfo(String number, Date dateFrom, Time timeFrom, Date dateTo, Time timeTo, Set<String> idStockSet,
                        List<String> stopListItemList, Map<String, Set<String>> handlerDirectoryMap) {
        this.number = number;
        this.dateFrom = dateFrom;
        this.timeFrom = timeFrom;
        this.dateTo = dateTo;
        this.timeTo = timeTo;
        this.idStockSet = idStockSet;
        this.stopListItemList = stopListItemList;
        this.handlerDirectoryMap = handlerDirectoryMap;
    }
}
