package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalMercuryCustomOperationClientAction implements ClientAction {

    int type;

    public FiscalMercuryCustomOperationClientAction(int type) {
        this.type = type;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            FiscalMercury.init();
            if (FiscalMercury.login(FiscalMercury.ADMIN, "2222222", "Кассир")) {
                switch (type) {
                    case 1:
                        FiscalMercury.xReport();
                        break;
                    case 2:
                        FiscalMercury.zReport();
                        break;
                    case 3:
                        FiscalMercury.advancePaper(5);
                        break;
                    case 4:
                        FiscalMercury.cancelReceipt();
                        break;
                    case 5:
                        FiscalMercury.cutReceipt();
                        break;
                    default:
                        break;
                }
                FiscalMercury.logout();
            }
            return null;
        } catch (RuntimeException e) {
            return e.toString();
        }
    }
}
