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
    String denominationStage;

    public FiscalVMKPrintInvoicePaymentClientAction(String ip, Integer comPort, Integer baudRate, Integer placeNumber, Integer operatorNumber,
                                                    BigDecimal sumPayment, Integer typePayment, boolean sale, String denominationStage) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.sale = sale;
        this.denominationStage = denominationStage;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        synchronized (FiscalVMK.lock) {
            try {
                FiscalVMK.init();

                FiscalVMK.openPort(ip, comPort, baudRate);
                FiscalVMK.opensmIfClose();

                Integer numberReceipt = printPayment(sumPayment, typePayment, denominationStage);

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
    }

    private Integer printPayment(BigDecimal sumPayment, Integer typePayment, String denominationStage) {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return null;

        Integer receiptNumber = FiscalVMK.getReceiptNumber(true);

        if (sumPayment == null || !FiscalVMK.registerItemPayment(sumPayment, denominationStage))
            return null;

        if (!FiscalVMK.subtotal())
            return null;

        if (!FiscalVMK.total(sumPayment, typePayment, denominationStage))
            return null;
        return receiptNumber;
    }
}
