package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;


public class FiscalVMKPrintReceiptClientAction implements ClientAction {

    String ip;
    int comPort;
    int baudRate;
    int placeNumber;
    int operatorNumber;
    ReceiptInstance receipt;
    String receiptTop;
    String receiptBottom;
    boolean giftCardAsDiscount;

    public FiscalVMKPrintReceiptClientAction(String ip, Integer comPort, Integer baudRate, Integer placeNumber, Integer operatorNumber,
                                             ReceiptInstance receipt, String receiptTop, String receiptBottom, boolean giftCardAsDiscount) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.receipt = receipt;
        this.receiptTop = receiptTop;
        this.receiptBottom = receiptBottom;
        this.giftCardAsDiscount = giftCardAsDiscount;
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

                FiscalVMK.openPort(ip, comPort, baudRate);
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
                FiscalVMK.logReceipt(receipt, numberReceipt);

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
        
        FiscalVMK.printFiscalText(receiptTop);
        
        for (ReceiptItem item : receiptList) {
            if (!FiscalVMK.registerItem(item))
                return null;
            if (!FiscalVMK.discountItem(item))
                return null;
            DecimalFormat formatter = getFormatter();
            if(item.bonusSum != 0)
                FiscalVMK.printFiscalText("Начислено бонусных баллов:\n" + formatter.format(item.bonusSum));
            if(item.bonusPaid != 0)
                FiscalVMK.printFiscalText("Оплачено бонусными баллами:\n" + formatter.format(item.bonusPaid));
        }

        if (!FiscalVMK.subtotal())
            return null;
        if (!FiscalVMK.discountReceipt(receipt))
            return null;
        
        FiscalVMK.printFiscalText(receiptBottom);
        
        if (!FiscalVMK.totalGiftCard(receipt.sumGiftCard, giftCardAsDiscount))
            return null;
        if (!FiscalVMK.totalCard(receipt.sumCard))
            return null;
        if (!FiscalVMK.totalCash(receipt.sumCash))
            return null;
        return receiptNumber;
    }

    private DecimalFormat getFormatter() {
        DecimalFormat formatter = new DecimalFormat("#,###.00");
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
        symbols.setGroupingSeparator('`');
        formatter.setDecimalFormatSymbols(symbols);
        return formatter;
    }
}
