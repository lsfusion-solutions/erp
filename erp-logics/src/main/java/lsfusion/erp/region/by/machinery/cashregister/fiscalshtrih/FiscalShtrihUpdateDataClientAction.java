package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalShtrihUpdateDataClientAction implements ClientAction {

    int password;
    int comPort;
    int baudRate;
    UpdateDataInstance updateData;

    public FiscalShtrihUpdateDataClientAction(int password, Integer comPort, Integer baudRate, UpdateDataInstance updateData) {
        this.password = password;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.updateData = updateData;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        FiscalShtrih.init();
        try {

            FiscalShtrih.openPort(password, comPort, baudRate);
            //FiscalShtrih.resetTable(2);
            //if (updateData.operatorList.isEmpty())
            //    updateData.operatorList.add(new UpdateDataOperator(1000, 1, "Кассир по умолчанию"));
            for (int i = 1; i <= 28/*updateData.operatorList.size()*/; i++) {
                FiscalShtrih.setOperatorName(updateData.operatorList.size() <= (i - 1) ? new UpdateDataOperator(i * 1000, i, "Кассир по умолчанию") : updateData.operatorList.get(i - 1));
                Thread.sleep(1000);
            }

            //FiscalShtrih.resetTable(6);
            for (int i = 1; i <= /*Math.min(updateData.taxRateList.size(),*/ 4/*)*/; i++) {
                FiscalShtrih.setTaxRate(updateData.taxRateList.size() <= (i - 1) ? new UpdateDataTaxRate(i, BigDecimal.ZERO) : updateData.taxRateList.get(i - 1));
                Thread.sleep(1000);
            }

            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        } catch (InterruptedException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
