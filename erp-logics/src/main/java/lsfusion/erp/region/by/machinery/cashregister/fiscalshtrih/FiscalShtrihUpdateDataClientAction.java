package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


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

            FiscalShtrih.resetOperatorTable();
            if(updateData.operatorList.isEmpty())
                updateData.operatorList.add(new UpdateDataOperator(1, "Кассир по умолчанию"));
            for (int i = 1; i <= Math.min(updateData.operatorList.size(), 29); i++) {
                FiscalShtrih.setOperatorName(updateData.operatorList.get(i-1), i);
            }

            /*Double[] rates = new Double[4];
            for (UpdateDataTaxRate rate : updateData.taxRateList) {
                if(rate.taxRateNumber<=4)
                rates[rate.taxRateNumber - 1] = rate.taxRateValue;
            }
            String code = "";
            for (Double rate : rates) {
                code += (rate == null ? 0 : 1);
            }

            FiscalShtrih.setMulDecCurRF(code, rates);
            FiscalShtrih.closePort();*/

        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
