package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalEpsonResetReceiptClientAction implements ClientAction {
    Integer comPort;
    Integer baudRate;
    String cashier;
    Integer documentNumberReceipt;
    BigDecimal totalSum;
    BigDecimal sumCash;
    BigDecimal sumCard;
    BigDecimal sumGiftCard;
    Integer cardType;
    Integer giftCardType;

    public FiscalEpsonResetReceiptClientAction(Integer comPort, Integer baudRate, String cashier, Integer documentNumberReceipt, BigDecimal totalSum, BigDecimal sumCash, BigDecimal sumCard, BigDecimal sumGiftCard, Integer cardType, Integer giftCardType) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.cashier = cashier;
        this.documentNumberReceipt = documentNumberReceipt;
        this.totalSum = totalSum;
        this.sumCash = sumCash;
        this.sumCard = sumCard;
        this.sumGiftCard = sumGiftCard;
        this.cardType = cardType;
        this.giftCardType = giftCardType;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            FiscalEpson.resetReceipt(cashier, documentNumberReceipt, totalSum, sumCash, sumCard, sumGiftCard, cardType, giftCardType);

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }
        return null;
    }
}