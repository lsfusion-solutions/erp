package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import jssc.SerialPort;
import lsfusion.interop.action.ClientActionDispatcher;

public class FiscalPiritPrintReceiptClientAction extends FiscalPiritClientAction {
    ReceiptInstance receipt;
    Integer giftCardDepartment;
    Integer giftCardPaymentType;
    Integer saleGiftCardPaymentType;

    Integer versionPirit;

    String emailPhone;

    public FiscalPiritPrintReceiptClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier, ReceiptInstance receipt,
                                               Integer giftCardDepartment, Integer giftCardPaymentType, Integer saleGiftCardPaymentType,
                                               Integer versionPirit, String emailPhone) {
        super(isUnix, comPort, baudRate, cashier);
        this.receipt = receipt;
        this.giftCardDepartment = giftCardDepartment;
        this.giftCardPaymentType = giftCardPaymentType;
        this.saleGiftCardPaymentType = saleGiftCardPaymentType;
        this.versionPirit = versionPirit;
        this.emailPhone = emailPhone;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        if (!receipt.receiptSaleList.isEmpty() && receipt.receiptReturnList.size() != 0) {
            return "В одном чеке обнаружены продажи и возврат одновременно";
        }

        //защита от случая, когда сумма сертификата + сумма карточкой больше общей суммы.
        else if (receipt.sumGiftCard != null && receipt.sumCard != null && receipt.sumTotal != null && receipt.sumGiftCard.add(receipt.sumCard).doubleValue() > receipt.sumTotal.doubleValue()) {
            return "Сумма сертификата и сумма оплаты по карточке больше общей суммы чека";
        } else {
            SerialPort serialPort = null;
            try {
                serialPort = FiscalPirit.openPort(comPort, baudRate, isUnix);
                FiscalPirit.preparePrint(serialPort);

                Integer numberReceipt = null;

                if (!receipt.receiptSaleList.isEmpty()) {
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptSaleList, giftCardDepartment,
                            giftCardPaymentType, saleGiftCardPaymentType, true, versionPirit, emailPhone);
                }

                if (!receipt.receiptReturnList.isEmpty()) {
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptReturnList, giftCardDepartment,
                            giftCardPaymentType, saleGiftCardPaymentType, false, versionPirit, emailPhone);
                }
                return numberReceipt;
            } catch (RuntimeException e) {
                FiscalPirit.cancelDocument(serialPort);
                return e.getMessage() != null ? e.getMessage() : e.toString();
            } finally {
                FiscalPirit.closePort(serialPort);
            }
        }
    }
}
