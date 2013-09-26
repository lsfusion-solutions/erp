package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;


public class FiscalCasbiPrintReceiptClientAction implements ClientAction {

    ReceiptInstance receipt;
    int baudRate;
    int comPort;
    int placeNumber;
    int operatorNumber;

    public FiscalCasbiPrintReceiptClientAction(Integer baudRate, Integer comPort, Integer placeNumber,
                                               Integer operatorNumber, ReceiptInstance receipt) {
        this.receipt = receipt;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if (receipt.sumCard != null && receipt.sumCash != null)
            return "Смешанный платёж не поддерживается";

        if (receipt.receiptSaleList.size() != 0 && receipt.receiptReturnList.size() != 0)
            return "В одном чеке обнаружены продажи и возврат одновременно";

        if (receipt.receiptSaleList.size() > 31 || receipt.receiptReturnList.size() > 31)
            return "Слишком много позиций в чеке";

        try {
            FiscalCasbi.init();

            FiscalCasbi.openPort(comPort, baudRate);
            if (receipt.receiptSaleList.size() != 0) {
                if (!printReceipt(receipt.receiptSaleList, true)) {
                    String error = FiscalCasbi.getError(false);
                    FiscalCasbi.cancelReceipt();
                    return error;
                }
            }
            if (receipt.receiptReturnList.size() != 0)
                if (!printReceipt(receipt.receiptReturnList, false)) {
                    String error = FiscalCasbi.getError(false);
                    FiscalCasbi.cancelReceipt();
                    return error;
                }

            FiscalCasbi.closePort();

        } catch (RuntimeException e) {
            FiscalCasbi.cancelReceipt();
            return FiscalCasbi.getError(true);
        }
        return null;
    }

    private boolean printReceipt(List<ReceiptItem> receiptList, boolean sale) throws UnsupportedEncodingException {

        for (int i = 0; i < receiptList.size(); i++) {
            if (!FiscalCasbi.registerItem(receiptList.get(i), i, sale))
                return false;
        }

        if (!FiscalCasbi.totalCard(receipt.sumCard))
            return false;
        if (!FiscalCasbi.totalCash(receipt.sumCash))
            return false;
        return true;
    }
}
