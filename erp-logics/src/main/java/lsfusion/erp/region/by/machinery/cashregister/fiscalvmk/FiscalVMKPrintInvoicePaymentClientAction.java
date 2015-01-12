package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKPrintInvoicePaymentClientAction implements ClientAction {

    int baudRate;
    int comPort;
    int placeNumber;
    int operatorNumber;
    BigDecimal sumPayment;
    Integer typePayment;

    public FiscalVMKPrintInvoicePaymentClientAction(Integer baudRate, Integer comPort, Integer placeNumber, Integer operatorNumber,
                                                    BigDecimal sumPayment, Integer typePayment) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(comPort, baudRate);
            FiscalVMK.opensmIfClose();

            Integer numberReceipt = printPayment(sumPayment, typePayment);
            
            if (numberReceipt == null) {
                String error = FiscalVMK.getError(false);
                FiscalVMK.cancelReceipt();
                return error;
            }
            
            FiscalVMK.closePort();

            return numberReceipt;
        } catch (RuntimeException e) {
            FiscalVMK.cancelReceipt();
            return FiscalVMK.getError(true);
        }
    }

    private Integer printPayment(BigDecimal sumPayment, Integer typePayment) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(0))
            return null;

        Integer receiptNumber = FiscalVMK.getReceiptNumber(true);

        if (sumPayment == null || !FiscalVMK.registerItemPayment(sumPayment.longValue()))
            return null;

        if (!FiscalVMK.subtotal())
            return null;

        if (!FiscalVMK.total(sumPayment, typePayment))
            return null;
        return receiptNumber;
    }
}
