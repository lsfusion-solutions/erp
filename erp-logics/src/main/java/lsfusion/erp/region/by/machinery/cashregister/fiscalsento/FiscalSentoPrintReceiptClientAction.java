package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

import java.util.List;


public class FiscalSentoPrintReceiptClientAction extends FiscalSentoClientAction {
    ReceiptInstance receipt;
    String receiptTop;
    String receiptBottom;

    public FiscalSentoPrintReceiptClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate,
                                               ReceiptInstance receipt, String receiptTop, String receiptBottom) {
        super(isUnix, logPath, comPort, baudRate);
        this.receipt = receipt;
        this.receiptTop = receiptTop;
        this.receiptBottom = receiptBottom;
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
            try {
                FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
                FiscalSento.opensmIfClose();

                Integer numberReceipt = null;

                if (receipt.receiptSaleList.size() != 0) {
                    numberReceipt = printReceipt(receipt.receiptSaleList, true);
                }

                if (receipt.receiptReturnList.size() != 0) {
                    numberReceipt = printReceipt(receipt.receiptReturnList, false);
                }


                FiscalSento.closePort();
                FiscalSento.logReceipt(receipt, numberReceipt);

                return numberReceipt;
            } catch (RuntimeException e) {
                FiscalSento.cancelReceipt();
                return e.getMessage();
            } finally {
                FiscalSento.closePort();
            }
        }
    }

    private Integer printReceipt(List<ReceiptItem> receiptList, boolean sale) {

        if (!sale) {
            if(receiptList.size() == 1) {
                FiscalSento.openRefundDocument(receiptList.get(0));
                FiscalSento.discountItem(receiptList.get(0));
            } else {
                throw new RuntimeException("В чеке возврата может быть только 1 строка");
            }
        }
        FiscalSento.printFiscalText(receiptTop);

        if(sale) {
            for (int i = 0; i < receiptList.size(); i++) {
                FiscalSento.registerItem(receiptList.get(i), i == 0 ? receiptTop : null);
                FiscalSento.discountItem(receiptList.get(i));
            }
        }

        FiscalSento.printFiscalText(receiptBottom);

        FiscalSento.closeDocument(receipt);

        return FiscalSento.getReceiptNumber();
    }
}
