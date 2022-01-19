package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import jssc.SerialPort;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

public class FiscalPiritPrintReceiptClientAction extends FiscalPiritClientAction {
    ReceiptInstance receipt;
    Integer giftCardDepartment;
    Integer giftCardPaymentType;
    Integer saleGiftCardPaymentType;
    String prefixFFD12;

    public FiscalPiritPrintReceiptClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier, ReceiptInstance receipt,
                                               Integer giftCardDepartment, Integer giftCardPaymentType, Integer saleGiftCardPaymentType, String prefixFFD12) {
        super(isUnix, comPort, baudRate, cashier);
        this.receipt = receipt;
        this.giftCardDepartment = giftCardDepartment;
        this.giftCardPaymentType = giftCardPaymentType;
        this.saleGiftCardPaymentType = saleGiftCardPaymentType;
        this.prefixFFD12 = prefixFFD12;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        if (receipt.receiptSaleList.size() != 0 && receipt.receiptReturnList.size() != 0) {
            new MessageClientAction("В одном чеке обнаружены продажи и возврат одновременно", "Ошибка!");
            return "В одном чеке обнаружены продажи и возврат одновременно";
        }

        //защита от случая, когда сумма сертификата + сумма карточкой больше общей суммы.
        else if (receipt.sumGiftCard != null && receipt.sumCard != null && receipt.sumTotal != null && receipt.sumGiftCard.add(receipt.sumCard).doubleValue() > receipt.sumTotal.doubleValue()) {
            new MessageClientAction("Сумма сертификата и сумма оплаты по карточке больше общей суммы чека", "Ошибка!");
            return "Сумма сертификата и сумма оплаты по карточке больше общей суммы чека";
        } else {
            SerialPort serialPort = null;
            try {
                serialPort = FiscalPirit.openPort(comPort, baudRate, isUnix);
                FiscalPirit.preparePrint(serialPort);

                Integer numberReceipt = null;

                if (receipt.receiptSaleList.size() != 0) {
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptSaleList, giftCardDepartment, giftCardPaymentType, saleGiftCardPaymentType, prefixFFD12, true);
                }

                if (receipt.receiptReturnList.size() != 0) {
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptReturnList, giftCardDepartment, giftCardPaymentType, saleGiftCardPaymentType, prefixFFD12, false);
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
