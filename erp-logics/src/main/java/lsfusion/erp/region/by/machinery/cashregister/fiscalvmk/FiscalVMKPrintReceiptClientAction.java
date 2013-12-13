package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

import java.io.IOException;
import java.util.List;


public class FiscalVMKPrintReceiptClientAction implements ClientAction {

    ReceiptInstance receipt;
    int baudRate;
    int comPort;
    int placeNumber;
    int operatorNumber;

    public FiscalVMKPrintReceiptClientAction(Integer baudRate, Integer comPort, Integer placeNumber,
                                             Integer operatorNumber, ReceiptInstance receipt) {
        this.receipt = receipt;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if(receipt.sumTotal != null && receipt.sumTotal.doubleValue() > 15000000) {
            new MessageClientAction("Сумма чека превышает 15.000.000 рублей", "Ошибка!");
            return "Сумма чека превышает 15.000.000 рублей";
        }
                    
        if (receipt.receiptSaleList.size() != 0 && receipt.receiptReturnList.size() != 0) {
            new MessageClientAction("В одном чеке обнаружены продажи и возврат одновременно", "Ошибка!");
            return "В одном чеке обнаружены продажи и возврат одновременно";
        }

        //защита от случая, когда сумма сертификата + сумма карточкой больше общей суммы.
        else if (receipt.sumGiftCard != null && receipt.sumCard != null && receipt.sumTotal != null && receipt.sumGiftCard.add(receipt.sumCard).doubleValue() > receipt.sumTotal.doubleValue()) {
            new MessageClientAction("Сумма сертификата и сумма оплаты по карточке больше общей суммы чека", "Ошибка!");
            return "Сумма сертификата и сумма оплаты по карточке больше общей суммы чека";
        }

        else {
            try {
                FiscalVMK.init();

                FiscalVMK.openPort(comPort, baudRate);
                FiscalVMK.opensmIfClose();
                if (receipt.receiptSaleList.size() != 0)
                    if (!printReceipt(receipt.receiptSaleList, true)) {
                        String error = FiscalVMK.getError(false);
                        FiscalVMK.cancelReceipt();
                        return error;
                    }
                if (receipt.receiptReturnList.size() != 0)
                    if (!printReceipt(receipt.receiptReturnList, false)) {
                        String error = FiscalVMK.getError(false);
                        FiscalVMK.cancelReceipt();
                        return error;
                    }

                FiscalVMK.closePort();

            } catch (RuntimeException e) {
                FiscalVMK.cancelReceipt();
                return FiscalVMK.getError(true);
            }
        }
        return null;
    }

    private boolean printReceipt(List<ReceiptItem> receiptList, boolean sale) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return false;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return false;

        for (ReceiptItem item : receiptList) {
            if (!FiscalVMK.registerItem(item))
                return false;
            if (!FiscalVMK.discountItem(item))
                return false;
        }

        if (!FiscalVMK.subtotal())
            return false;

        if (!FiscalVMK.totalGiftCard(receipt.sumGiftCard))
            return false;
        if (!FiscalVMK.totalCard(receipt.sumCard))
            return false;
        if (!FiscalVMK.totalCash(receipt.sumCash))
            return false;
        return true;
    }
}
