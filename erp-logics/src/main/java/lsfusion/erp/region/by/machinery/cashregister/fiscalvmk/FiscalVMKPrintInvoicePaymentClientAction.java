package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKPrintInvoicePaymentClientAction implements ClientAction {

    String ip;
    int comPort;
    int baudRate;
    int placeNumber;
    int operatorNumber;
    BigDecimal sumPayment;
    Integer typePayment;
    boolean sale;
    boolean denominate;

    public FiscalVMKPrintInvoicePaymentClientAction(String ip, Integer comPort, Integer baudRate, Integer placeNumber, Integer operatorNumber,
                                                    BigDecimal sumPayment, Integer typePayment, boolean sale, boolean denominate) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.sale = sale;
        this.denominate = denominate;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(ip, comPort, baudRate);
            FiscalVMK.opensmIfClose();

            Integer numberReceipt = printPayment(sumPayment, typePayment, denominate);
            
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

    private Integer printPayment(BigDecimal sumPayment, Integer typePayment, boolean denominate) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return null;

        Integer receiptNumber = FiscalVMK.getReceiptNumber(true);

        if (sumPayment == null || !FiscalVMK.registerItemPayment(sumPayment.doubleValue(), denominate))
            return null;

        if (!FiscalVMK.subtotal())
            return null;

        if (!FiscalVMK.total(sumPayment, typePayment, denominate))
            return null;
        return receiptNumber;
    }
}
