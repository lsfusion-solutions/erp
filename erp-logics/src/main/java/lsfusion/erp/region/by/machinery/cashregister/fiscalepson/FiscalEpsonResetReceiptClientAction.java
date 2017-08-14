package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalEpsonResetReceiptClientAction implements ClientAction {
    Integer comPort;
    Integer baudRate;
    String cashier;
    Integer numberReceipt;
    BigDecimal totalSum;
    BigDecimal sumCash;
    BigDecimal sumCard;
    BigDecimal sumGiftCard;

    public FiscalEpsonResetReceiptClientAction(Integer comPort, Integer baudRate, String cashier, Integer numberReceipt, BigDecimal totalSum, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.cashier = cashier;
        this.numberReceipt = numberReceipt;
        this.totalSum = totalSum;
        this.sumCash = sumCash;
        this.sumCard = sumCard;
        this.sumGiftCard = sumGiftCard;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            FiscalEpson.resetReceipt(cashier, numberReceipt, totalSum, sumCash, sumCard, sumGiftCard);

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }
        return null;
    }
}