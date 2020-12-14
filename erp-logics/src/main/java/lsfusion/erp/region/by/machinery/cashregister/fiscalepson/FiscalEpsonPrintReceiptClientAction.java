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
    int modificationType;
    
    public FiscalEpsonPrintReceiptClientAction(Integer comPort, Integer baudRate, Boolean isReturn, ReceiptInstance receipt, Integer cardType, Integer giftCardType, Integer modificationType) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.isReturn = isReturn;
        this.receipt = receipt;
        this.cardType = cardType;
        this.giftCardType = giftCardType;
        this.modificationType = modificationType == null ? 0 : modificationType;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            return FiscalEpson.printReceipt(receipt, !isReturn, cardType, giftCardType, modificationType);

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return new PrintReceiptResult(e.getMessage());
        } finally {
            FiscalEpson.closePort();
        }
    }
}