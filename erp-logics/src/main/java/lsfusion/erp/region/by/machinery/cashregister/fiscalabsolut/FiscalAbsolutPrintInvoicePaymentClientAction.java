package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalAbsolutPrintInvoicePaymentClientAction implements ClientAction {

    String logPath;
    int comPort;
    int baudRate;
    private BigDecimal sumPayment;
    private Integer typePayment;
    private boolean sale;
    private boolean saveCommentOnFiscalTape;

    FiscalAbsolutPrintInvoicePaymentClientAction(String logPath, Integer comPort, Integer baudRate,
                                                 BigDecimal sumPayment, Integer typePayment, boolean sale, boolean saveCommentOnFiscalTape) {
        this.logPath = logPath;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.sale = sale;
        this.saveCommentOnFiscalTape = saveCommentOnFiscalTape;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) {
        try {
            FiscalAbsolut.openPort(logPath, comPort, baudRate);
            FiscalAbsolut.smenBegin();

            if (!printPayment(sumPayment, typePayment)) {
                String error = FiscalAbsolut.getError(false);
                FiscalAbsolut.cancelReceipt();
                return error;
            } else {
                FiscalAbsolut.closeReceipt();
            }
        } catch (RuntimeException e) {
            FiscalAbsolut.cancelReceipt();
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
        return null;
    }

    private boolean printPayment(BigDecimal sumPayment, Integer typePayment) {

        if (!FiscalAbsolut.openReceipt(sale))
            return false;

        if (sumPayment == null || !FiscalAbsolut.registerItemPayment(sumPayment, saveCommentOnFiscalTape))
            return false;

        if (!FiscalAbsolut.subtotal())
            return false;

        if (!FiscalAbsolut.total(sumPayment, typePayment))
            return false;
        return true;
    }
}
