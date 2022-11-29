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
    boolean resetTypeOfGoods;
    
    public FiscalEpsonPrintReceiptClientAction(Integer comPort, Integer baudRate, Boolean isReturn, ReceiptInstance receipt, Integer cardType, Integer giftCardType,
                                               boolean sendSKNO, boolean resetTypeOfGoods) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.isReturn = isReturn;
        this.receipt = receipt;
        this.cardType = cardType;
        this.giftCardType = giftCardType;
        this.sendSKNO = sendSKNO;
        this.resetTypeOfGoods = resetTypeOfGoods;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            return FiscalEpson.printReceipt(receipt, !isReturn, cardType, giftCardType, sendSKNO, resetTypeOfGoods);

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