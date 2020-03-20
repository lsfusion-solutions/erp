package lsfusion.erp.region.ru.masterdata;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Exchange {
    String currencyID;
    String homeCurrencyID;
    LocalDate date;
    BigDecimal exchangeRate;

    public Exchange(String currencyID, String homeCurrencyID, LocalDate date, BigDecimal exchangeRate) {
        this.currencyID = currencyID;
        this.homeCurrencyID = homeCurrencyID;
        this.date = date;
        this.exchangeRate = exchangeRate;
    }
}
