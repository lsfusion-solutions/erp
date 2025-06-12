package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import static lsfusion.base.BaseUtils.nvl;


public class FiscalSentoPrintInvoicePaymentClientAction extends FiscalSentoClientAction {
    BigDecimal sumPayment;
    Integer typePayment;
    Integer numberSection;
    boolean sale;
    List<InvoiceDetail> invoiceDetailList;

    public FiscalSentoPrintInvoicePaymentClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate,
                                                      BigDecimal sumPayment, Integer typePayment, Integer numberSection,
                                                      boolean sale, List<InvoiceDetail> invoiceDetailList) {
        super(isUnix, logPath, comPort, baudRate);
        this.sumPayment = sumPayment;
        this.typePayment = typePayment;
        this.numberSection = numberSection;
        this.sale = sale;
        this.invoiceDetailList = invoiceDetailList;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
    
            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
            FiscalSento.opensmIfClose();

            Integer numberReceipt = printPayment();
            
            if (numberReceipt == null) {
                int lastError = FiscalSento.sentoDLL.sento.lastError();
                String error = FiscalSento.getError(lastError);
                FiscalSento.cancelReceipt();
                FiscalSento.closePort();
                return error;
            }

            FiscalSento.closePort();

            return numberReceipt;
            
        } catch (RuntimeException | UnsupportedEncodingException e) {
            FiscalSento.cancelReceipt();
            int lastError = FiscalSento.sentoDLL.sento.lastError();
            return FiscalSento.getError(lastError);
        }
    }

    private Integer printPayment() throws UnsupportedEncodingException {
    
        int section = nvl(numberSection, 1);
    
        if (invoiceDetailList.isEmpty()) {
            if (sumPayment == null)
                return null;
            
            if (sale) {
                if (!FiscalSento.sentoDLL.sento.sale((short) 6, section, ("1" + "\0").getBytes(), 'A',
                        sumPayment.doubleValue(), 1.0, sumPayment.doubleValue(),
                        ("ОПЛАТА" + "\0").getBytes(Charset.forName("cp1251")), ("" + "\0").getBytes()))
                    return null;
            }
            else {
                if (!FiscalSento.sentoDLL.sento.openRefundDocument(section, ("1" + "\0").getBytes(), 'A',
                        sumPayment.doubleValue(), 1.0, sumPayment.doubleValue(),
                        ("ОПЛАТА" + "\0").getBytes(Charset.forName("cp1251"))))
                    return null;
            }
        } else {
            if (sale) {
                for(InvoiceDetail detail : invoiceDetailList) {
                    if (!FiscalSento.sentoDLL.sento.sale((short) 6, section, ("1"+"\0").getBytes(), 'A',
                            detail.price.doubleValue(), detail.quantity.doubleValue(), detail.sum.doubleValue(),
                            (detail.name+"\0").getBytes(Charset.forName("cp1251")), (""+"\0").getBytes()))
                        return null;
                }
            }
            else {
                if (!FiscalSento.sentoDLL.sento.openRefundDocument(section, ("1"+"\0").getBytes(), 'A',
                        invoiceDetailList.get(0).price.doubleValue(), invoiceDetailList.get(0).quantity.doubleValue(), invoiceDetailList.get(0).sum.doubleValue(),
                        (invoiceDetailList.get(0).name+"\0").getBytes(Charset.forName("cp1251"))))
                    return null;
            }
        }
    
        double sumCash = 0;
        double sumCard = 0;
        
        if (typePayment == 0)
            sumCash = sumPayment.doubleValue();
        else
            sumCard = sumPayment.doubleValue();
        
        if (!FiscalSento.sentoDLL.sento.closeDocument(sumPayment.doubleValue(), sumCash, 0, sumCard, 0, 0))
            return null;
    
        return FiscalSento.getReceiptNumber();
    }
}
