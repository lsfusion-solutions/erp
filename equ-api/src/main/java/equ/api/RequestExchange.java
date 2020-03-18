package equ.api;

import equ.api.cashregister.CashRegisterInfo;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

public class RequestExchange implements Serializable {
    public Long requestExchange;
    public Set<CashRegisterInfo> cashRegisterSet;
    public Set<CashRegisterInfo> extraCashRegisterSet;
    public String idStock;
    public LocalDate dateFrom;
    public LocalDate dateTo;
    public LocalDate startDate;
    private String typeRequestExchange;

    public RequestExchange(Long requestExchange, Set<CashRegisterInfo> cashRegisterSet, Set<CashRegisterInfo> extraCashRegisterSet,
                           String idStock, LocalDate dateFrom, LocalDate dateTo, LocalDate startDate, String typeRequestExchange) {
        this.requestExchange = requestExchange;
        this.cashRegisterSet = cashRegisterSet;
        this.extraCashRegisterSet = extraCashRegisterSet;
        this.idStock = idStock;
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

    public boolean isCashier() {
        return typeRequestExchange != null && typeRequestExchange.contains("cashierInfo");
    }
    
}
