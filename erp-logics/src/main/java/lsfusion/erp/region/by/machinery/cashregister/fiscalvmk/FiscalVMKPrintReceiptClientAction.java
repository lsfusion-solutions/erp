package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

import java.io.IOException;
import java.util.List;


public class FiscalVMKPrintReceiptClientAction implements ClientAction {
    
    int baudRate;
    int comPort;
    int placeNumber;
    int operatorNumber;
    ReceiptInstance receipt;
    String zReportTop;
    String zReportBottom;

    public FiscalVMKPrintReceiptClientAction(Integer baudRate, Integer comPort, Integer placeNumber, Integer operatorNumber, 
                                             ReceiptInstance receipt, String zReportTop, String zReportBottom) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.receipt = receipt;
        this.zReportTop = zReportTop;
        this.zReportBottom = zReportBottom;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if (receipt.receiptSaleList.size() != 0 && receipt.receiptReturnList.size() != 0) {
            new MessageClientAction("В одном чеке обнаружены продажи и возврат одновременно", "Ошибка!");
            return "В одном чеке обнаружены продажи и возврат одновременно";
        }

        //защита от случая, когда сумма сертификата + сумма карточкой больше общей суммы.
        else if (receipt.sumGiftCard != null && receipt.sumCard != null && receipt.sumTotal != null && receipt.sumGiftCard.add(receipt.sumCard).doubleValue() > receipt.sumTotal.doubleValue()) {
            new MessageClientAction("Сумма сертификата и сумма оплаты по карточке больше общей суммы чека", "Ошибка!");
            return "Сумма сертификата и сумма оплаты по карточке больше общей суммы чека";
        } else {
            try {
                FiscalVMK.init();

                FiscalVMK.openPort(comPort, baudRate);
                FiscalVMK.opensmIfClose();
                
                Integer numberReceipt = null;
                
                if (receipt.receiptSaleList.size() != 0) {
                    numberReceipt = printReceipt(receipt.receiptSaleList, true);
                    if (numberReceipt == null) {
                        String error = FiscalVMK.getError(false);
                        FiscalVMK.cancelReceipt();
                        return error;
                    }
                }
                    
                if (receipt.receiptReturnList.size() != 0) {
                    numberReceipt = printReceipt(receipt.receiptReturnList, false);
                    if (numberReceipt == null) {
                        String error = FiscalVMK.getError(false);
                        FiscalVMK.cancelReceipt();
                        return error;
                    }
                }
                    

                FiscalVMK.closePort();

                return numberReceipt;
            } catch (RuntimeException e) {
                FiscalVMK.cancelReceipt();
                return FiscalVMK.getError(true);
            }
        }
    }

    private Integer printReceipt(List<ReceiptItem> receiptList, boolean sale) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return null;
        
        Integer receiptNumber = FiscalVMK.getReceiptNumber(true);
        
        FiscalVMK.printFiscalText(zReportTop);
        
        for (ReceiptItem item : receiptList) {
            if (!FiscalVMK.registerItem(item))
                return null;
            if (!FiscalVMK.discountItem(item))
                return null;
        }

        if (!FiscalVMK.subtotal())
            return null;
        if (!FiscalVMK.discountReceipt(receipt))
            return null;
        
        FiscalVMK.printFiscalText(zReportBottom);
        
        if (!FiscalVMK.totalGiftCard(receipt.sumGiftCard))
            return null;
        if (!FiscalVMK.totalCard(receipt.sumCard))
            return null;
        if (!FiscalVMK.totalCash(receipt.sumCash))
            return null;
        return receiptNumber;
    }
}
