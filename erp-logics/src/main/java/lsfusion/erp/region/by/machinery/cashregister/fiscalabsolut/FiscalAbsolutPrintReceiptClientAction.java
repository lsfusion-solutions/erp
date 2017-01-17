package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.MessageClientAction;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;


public class FiscalAbsolutPrintReceiptClientAction implements ClientAction {

    int comPort;
    int baudRate;
    int placeNumber;
    int operatorNumber;
    ReceiptInstance receipt;
    String receiptTop;
    String receiptBottom;
    String receiptCode128;
    boolean saveCommentOnFiscalTape;
    boolean groupPaymentsByVAT;
    boolean giftCardAsNotPayment;

    public FiscalAbsolutPrintReceiptClientAction(Integer comPort, Integer baudRate, Integer placeNumber, Integer operatorNumber,
                                                 ReceiptInstance receipt, String receiptTop, String receiptBottom,
                                                 String receiptCode128, boolean saveCommentOnFiscalTape, boolean groupPaymentsByVAT,
                                                 boolean giftCardAsNotPayment) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.placeNumber = placeNumber == null ? 1 : placeNumber;
        this.operatorNumber = operatorNumber == null ? 1 : operatorNumber;
        this.receipt = receipt;
        this.receiptTop = receiptTop;
        this.receiptBottom = receiptBottom;
        this.receiptCode128 = receiptCode128;
        this.saveCommentOnFiscalTape = saveCommentOnFiscalTape;
        this.groupPaymentsByVAT = groupPaymentsByVAT;
        this.giftCardAsNotPayment = giftCardAsNotPayment;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if (receipt.receiptSaleList.size() != 0 && receipt.receiptReturnList.size() != 0) {
            new MessageClientAction("В одном чеке обнаружены продажи и возврат одновременно", "Ошибка!");
            return "В одном чеке обнаружены продажи и возврат одновременно";
        }

        //защита от случая, когда сумма сертификата + сумма карточкой больше общей суммы.
        else if (receipt.sumGiftCard != null && receipt.sumCard != null && receipt.sumTotal != null && receipt.sumGiftCard.add(receipt.sumCard).doubleValue() > receipt.sumTotal.doubleValue()) {
            new MessageClientAction("Сумма сертификата и сумма оплаты по карточке больше общей суммы чека", "Ошибка!");
            return "Сумма сертификата и сумма оплаты по карточке больше общей суммы чека";
        } else {
            boolean opened = false;
            try {

                FiscalAbsolut.openPort(comPort, baudRate);
                FiscalAbsolut.smenBegin();


                if (receipt.receiptSaleList.size() != 0) {
                    opened = FiscalAbsolut.openReceipt(true);
                    if (opened) {
                        if (!printReceipt(receipt.receiptSaleList)) {
                            String error = FiscalAbsolut.getError(false);
                            FiscalAbsolut.cancelReceipt();
                            return error;
                        } else
                            FiscalAbsolut.closeReceipt();
                    } else
                        return FiscalAbsolut.getError(false);
                }

                if (receipt.receiptReturnList.size() != 0) {
                    opened = FiscalAbsolut.openReceipt(false);
                    if (opened) {
                        if (!printReceipt(receipt.receiptReturnList)) {
                            String error = FiscalAbsolut.getError(false);
                            FiscalAbsolut.cancelReceipt();
                            return error;
                        } else
                            FiscalAbsolut.closeReceipt();
                    } else
                        return FiscalAbsolut.getError(false);
                }
                return null;
            } catch (RuntimeException e) {
                boolean alreadyOpen = FiscalAbsolut.checkErrors(false) == 254;
                if(opened || alreadyOpen) //чек уже открыт
                    FiscalAbsolut.cancelReceipt();
                return alreadyOpen ? "Был закрыт предыдущий незакрытый чек. Запустите печать чека ещё раз" : FiscalAbsolut.getError(true);
            } finally {
                FiscalAbsolut.closePort();
            }
        }
    }

    private boolean printReceipt(List<ReceiptItem> receiptList) {

        FiscalAbsolut.printFiscalText(receiptTop);
        FiscalAbsolut.printBarcode(receiptCode128);

        if (giftCardAsNotPayment && receipt.sumGiftCard != null) {

            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal discountSum = BigDecimal.ZERO;
            DecimalFormat formatter = getFormatter();
            for (ReceiptItem item : receiptList) {
                double discount = item.articleDiscSum - item.bonusPaid;
                sum = sum.add(BigDecimal.valueOf(item.sumPos - discount));
                discountSum = discountSum.add(BigDecimal.valueOf(discount));
                FiscalAbsolut.printMultilineFiscalText(item.name);
                FiscalAbsolut.printFiscalText(getFiscalString("Код", item.barcode));
                FiscalAbsolut.printFiscalText(getFiscalString("Цена",
                        new DecimalFormat("#,###.##").format(item.quantity) + "x" + formatter.format(item.price)));
                if(discount != 0.0)
                    FiscalAbsolut.printFiscalText(getFiscalString("Скидка", formatter.format(discount)));
            }
            discountSum = discountSum.add(receipt.sumDisc == null ? BigDecimal.ZERO : receipt.sumDisc);
            sum = sum.subtract(receipt.sumGiftCard).max(BigDecimal.ZERO);

            FiscalAbsolut.printFiscalText(getFiscalString("Сертификат", formatter.format(receipt.sumGiftCard)));
            FiscalAbsolut.printFiscalText(getFiscalString("", " \n( __________ _______________ )"));
            FiscalAbsolut.printFiscalText(getFiscalString("", " (подпись)     ФИО      \n "));

            if (!FiscalAbsolut.registerAndDiscountItem(sum, discountSum))
                return false;

            if (!FiscalAbsolut.subtotal())
                return false;

            FiscalAbsolut.printFiscalText(receiptBottom);

            if (!FiscalAbsolut.totalCard(receipt.sumCard))
                return false;
            if (!FiscalAbsolut.totalCash(receipt.sumCash))
                return false;
            if(receipt.sumCard == null && receipt.sumCash == null && !sum.equals(BigDecimal.ZERO))
                if(!FiscalAbsolut.totalCash(BigDecimal.ZERO))
                    return false;

        } else {

            for (ReceiptItem item : receiptList) {
                if (!FiscalAbsolut.registerItem(item, saveCommentOnFiscalTape, groupPaymentsByVAT))
                    return false;
                if (!FiscalAbsolut.discountItem(item, receipt.numberDiscountCard))
                    return false;
                DecimalFormat formatter = getFormatter();
                if (item.bonusSum != 0.0) {
                    FiscalAbsolut.simpleLogAction("Дисконтная карта: " + receipt.numberDiscountCard);
                    FiscalAbsolut.printFiscalText("Начислено бонусных баллов:\n" + formatter.format(item.bonusSum));
                }
                if (item.bonusPaid != 0.0) {
                    FiscalAbsolut.simpleLogAction("Дисконтная карта: " + receipt.numberDiscountCard);
                    FiscalAbsolut.printFiscalText("Оплачено бонусными баллами:\n" + formatter.format(item.bonusPaid));
                }
            }

            if (!FiscalAbsolut.subtotal())
                return false;
            if (!FiscalAbsolut.discountReceipt(receipt))
                return false;

            FiscalAbsolut.printFiscalText(receiptBottom);

            if (!FiscalAbsolut.totalGiftCard(receipt.sumGiftCard))
                return false;
            if (!FiscalAbsolut.totalCard(receipt.sumCard))
                return false;
            if (!FiscalAbsolut.totalCash(receipt.sumCash))
                return false;

        }

        return true;
    }

    private String getFiscalString(String prefix, String value) {
        while(value.length() < 29 - prefix.length())
            value = " " + value;
        return prefix + " " + value;
    }

    private DecimalFormat getFormatter() {
        DecimalFormat formatter = new DecimalFormat("#,###.##");
        formatter.setMinimumFractionDigits(2);
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
        symbols.setGroupingSeparator('`');
        formatter.setDecimalFormatSymbols(symbols);
        return formatter;
    }
}
