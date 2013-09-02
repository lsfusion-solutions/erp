package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalMercuryServiceInOutClientAction implements ClientAction {

    BigDecimal sum;

    public FiscalMercuryServiceInOutClientAction(BigDecimal sum) {
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            char type = sum.longValue() > 0 ? FiscalMercury.CASH_IN : FiscalMercury.CASH_OUT;
            FiscalMercury.init();
            if (FiscalMercury.login(FiscalMercury.CASHIER, "1111111", "Кассир")) {

                FiscalMercury.inOut(type, sum.longValue());
                if (!FiscalMercury.openDrawer())
                    return "Не удалось открыть денежный ящик";
                FiscalMercury.logout();
            }
            return null;
        } catch (RuntimeException e) {
            FiscalMercury.cancelReceipt();
            return e.toString();
        }
    }
}