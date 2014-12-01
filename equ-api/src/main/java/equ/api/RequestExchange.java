package equ.api;

import java.io.Serializable;
import java.sql.Date;
import java.util.Set;

public class RequestExchange implements Serializable {
    public Integer requestExchange;
    public Set<String> directorySet;
    public String idStock;
    public Set<String> extraStockSet;
    public Date dateFrom;
    public Date dateTo;
    public Date startDate;
    private String typeRequestExchange;

    public RequestExchange(Integer requestExchange, Set<String> directorySet, String idStock, Set<String> extraStockSet, 
                           Date dateFrom, Date dateTo, Date startDate, String typeRequestExchange) {
        this.requestExchange = requestExchange;
        this.directorySet = directorySet;
        this.idStock = idStock;
        this.extraStockSet = extraStockSet;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.startDate = startDate;
        this.typeRequestExchange = typeRequestExchange;
    }

    public boolean isTerminalOrderExchange() {
        return typeRequestExchange != null && typeRequestExchange.contains("terminalOrder");
    }

    public boolean isSalesInfoExchange() {
        return typeRequestExchange != null && typeRequestExchange.contains("salesInfo");
    }

    public boolean isCheckZReportExchange() {
        return typeRequestExchange != null && typeRequestExchange.contains("checkZReport");
    }

    public boolean isDiscountCard() {
        return typeRequestExchange != null && typeRequestExchange.contains("discountCard");
    }
    
    public boolean isPromotion() {
        return typeRequestExchange != null && typeRequestExchange.contains("promotion");
    }
    
}
