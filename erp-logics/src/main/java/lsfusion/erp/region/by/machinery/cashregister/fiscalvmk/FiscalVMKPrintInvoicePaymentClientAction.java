package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.math.BigDecimal;
import java.util.List;

import static lsfusion.base.BaseUtils.nvl;


public class FiscalVMKPrintInvoicePaymentClientAction extends FiscalVMKClientAction {
    BigDecimal sumPayment;
    Integer typePayment;
    Integer numberSection;
    boolean sale;
    List<InvoiceDetail> invoiceDetailList;

    public FiscalVMKPrintInvoicePaymentClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate,
                                                    BigDecimal sumPayment, Integer typePayment, Integer numberSection,
                                                    boolean sale, List<InvoiceDetail> invoiceDetailList) {
        super(isUnix, logPath, ip, comPort, baudRate);
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.numberSection = numberSection;
        this.sale = sale;
        this.invoiceDetailList = invoiceDetailList;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {

            FiscalVMK.openPort(isUnix, logPath, ip, comPort, baudRate);
            FiscalVMK.opensmIfClose();

            Integer numberReceipt = printPayment();
            
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

    private Integer printPayment() {

        if (!FiscalVMK.getFiscalClosureStatus())
            return null;
        if (!FiscalVMK.openReceipt(sale ? 0 : 1))
            return null;

        Integer receiptNumber = FiscalVMK.getReceiptNumber();
        Integer section = nvl(numberSection, 1);

        if(invoiceDetailList.isEmpty()) {
            if (sumPayment == null || !FiscalVMK.registerItemPayment(sumPayment, section))
                return null;
        } else {
            for(InvoiceDetail detail : invoiceDetailList) {
                if(!FiscalVMK.registerItemPaymentDetail(detail, section))
                    return null;
            }
        }

        if (!FiscalVMK.subtotal())
            return null;

        if (!FiscalVMK.total(sumPayment, typePayment))
            return null;
        return receiptNumber;
    }
}
