package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKPrintInvoicePaymentClientAction implements ClientAction {

    String logPath;
    String ip;
    int comPort;
    int baudRate;
    BigDecimal sumPayment;
    Integer typePayment;
    boolean sale;

    public FiscalVMKPrintInvoicePaymentClientAction(String logPath, String ip, Integer comPort, Integer baudRate, BigDecimal sumPayment, Integer typePayment, boolean sale) {
        this.logPath = logPath;
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.sale = sale;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(logPath, ip, comPort, baudRate);
            FiscalVMK.opensmIfClose();

            Integer numberReceipt = printPayment(sumPayment, typePayment);
            
            if (numberReceipt == null) {
                String error = FiscalVMK.getError(false);
                FiscalVMK.cancelReceipt();
                return error;
            }
            
            FiscalVMK.closePort();

            return null;
        } catch (RuntimeException e) {
            FiscalVMK.cancelReceipt();
            return FiscalVMK.getError(true);
        }
    }

    private Integer printPayment(BigDecimal sumPayment, Integer typePayment) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return null;

        Integer receiptNumber = FiscalVMK.getReceiptNumber();

        if (sumPayment == null || !FiscalVMK.registerItemPayment(sumPayment))
            return null;

        if (!FiscalVMK.subtotal())
            return null;

        if (!FiscalVMK.total(sumPayment, typePayment))
            return null;
        return receiptNumber;
    }
}
