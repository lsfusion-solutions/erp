package equ.api.cashregister;

import java.io.Serializable;
import java.sql.Date;
import java.util.Set;

public class RequestExchange implements Serializable {
    public Integer requestExchange;
    public Set<String> directorySet;
    public String idStock;
    public Date dateFrom;
    public Date dateTo;
    public Boolean requestSalesInfo;

    public RequestExchange(Integer requestExchange, Set<String> directorySet, String idStock, Date dateFrom, Date dateTo,
                           Boolean requestSalesInfo) {
        this.requestExchange = requestExchange;
        this.directorySet = directorySet;
        this.idStock = idStock;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.requestSalesInfo = requestSalesInfo;
    }
}
