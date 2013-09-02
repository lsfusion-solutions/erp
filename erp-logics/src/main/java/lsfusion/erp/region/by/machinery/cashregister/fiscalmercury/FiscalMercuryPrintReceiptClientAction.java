package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalMercuryPrintReceiptClientAction implements ClientAction {

    ReceiptInstance receipt;
    Boolean isReturn;

    public FiscalMercuryPrintReceiptClientAction(ReceiptInstance receipt, Boolean isReturn) {
        this.receipt = receipt;
        this.isReturn = isReturn;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            FiscalMercury.init();
            if (FiscalMercury.login(FiscalMercury.CASHIER, "1111111", receipt.cashierName)) {

                FiscalMercury.printReceipt(isReturn ? FiscalMercury.RETURN : FiscalMercury.SALE, receipt);

                FiscalMercury.logout();
            }


            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
