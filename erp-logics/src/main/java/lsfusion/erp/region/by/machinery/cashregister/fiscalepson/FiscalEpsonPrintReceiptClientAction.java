package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonPrintReceiptClientAction implements ClientAction {
    int comPort;
    int baudRate;
    Boolean isReturn;
    ReceiptInstance receipt;
    Integer cardType;
    Integer giftCardType;
    boolean sendSKNO;
    
    public FiscalEpsonPrintReceiptClientAction(Integer comPort, Integer baudRate, Boolean isReturn, ReceiptInstance receipt, Integer cardType, Integer giftCardType, boolean sendSKNO) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.isReturn = isReturn;
        this.receipt = receipt;
        this.cardType = cardType;
        this.giftCardType = giftCardType;
        this.sendSKNO = sendSKNO;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            return FiscalEpson.printReceipt(receipt, !isReturn, cardType, giftCardType, sendSKNO);

        } catch (RuntimeException e) {
            try {
                FiscalEpson.cancelReceipt(false);
            } catch (Exception ignored) { //Нам важна первая ошибка
            }
            return new PrintReceiptResult(e.getMessage());
        } finally {
            FiscalEpson.closePort();
        }
    }
}