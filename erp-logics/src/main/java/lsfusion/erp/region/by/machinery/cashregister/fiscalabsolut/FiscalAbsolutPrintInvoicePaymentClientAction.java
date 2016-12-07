package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalAbsolutPrintInvoicePaymentClientAction implements ClientAction {

    int comPort;
    int baudRate;
    private int placeNumber;
    private int operatorNumber;
    private BigDecimal sumPayment;
    private Integer typePayment;
    private boolean sale;

    FiscalAbsolutPrintInvoicePaymentClientAction(Integer comPort, Integer baudRate, Integer placeNumber, Integer operatorNumber,
                                                 BigDecimal sumPayment, Integer typePayment, boolean sale) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.sale = sale;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        try {
            FiscalAbsolut.openPort(comPort, baudRate);
            FiscalAbsolut.smenBegin();

            if (!printPayment(sumPayment, typePayment)) {
                String error = FiscalAbsolut.getError(false);
                FiscalAbsolut.cancelReceipt();
                return error;
            } else {
                return FiscalAbsolut.closeReceipt();
            }
        } catch (RuntimeException e) {
            FiscalAbsolut.cancelReceipt();
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
    }

    private boolean printPayment(BigDecimal sumPayment, Integer typePayment) {

        if (!FiscalAbsolut.openReceipt(sale))
            return false;

        if (sumPayment == null || !FiscalAbsolut.registerItemPayment(sumPayment))
            return false;

        if (!FiscalAbsolut.subtotal())
            return false;

        if (!FiscalAbsolut.total(sumPayment, typePayment))
            return false;
        return true;
    }
}
