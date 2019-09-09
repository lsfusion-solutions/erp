package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import jssc.SerialPort;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

public class FiscalPiritPrintReceiptClientAction extends FiscalPiritClientAction {
    ReceiptInstance receipt;
    Integer giftCardPaymentType;

    public FiscalPiritPrintReceiptClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier, ReceiptInstance receipt, Integer giftCardPaymentType) {
        super(isUnix, comPort, baudRate, cashier);
        this.receipt = receipt;
        this.giftCardPaymentType = giftCardPaymentType;
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
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptSaleList, true);
                }

                if (receipt.receiptReturnList.size() != 0) {
                    numberReceipt = FiscalPirit.printReceipt(serialPort, cashier, receipt, receipt.receiptReturnList, false);
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
