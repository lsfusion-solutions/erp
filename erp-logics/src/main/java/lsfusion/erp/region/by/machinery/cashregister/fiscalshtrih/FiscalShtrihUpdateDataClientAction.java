package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

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


    public Object dispatch(ClientActionDispatcher dispatcher) {

        FiscalShtrih.init();
        try {

            FiscalShtrih.openPort(password, comPort, baudRate);
            
            for (int i = 1; i <= updateData.operatorList.size(); i++) {
                FiscalShtrih.setOperatorName(updateData.operatorList.get(i - 1));
            }

            for (int i = 1; i <=  4; i++) {
                FiscalShtrih.setTaxRate(updateData.taxRateList.size() <= (i - 1) ? new UpdateDataTaxRate(i, BigDecimal.ZERO) : updateData.taxRateList.get(i - 1));
            }

            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
