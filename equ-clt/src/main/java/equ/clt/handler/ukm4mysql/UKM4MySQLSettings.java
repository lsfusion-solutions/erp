package equ.clt.handler.ukm4mysql;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UKM4MySQLSettings implements Serializable{

    private String cashPayments;
    private String cardPayments;
    private String giftCardPayments;
    private List<String> giftCardList = new ArrayList<>();
    private Integer timeout;
    private Boolean skipItems;
    private Boolean skipClassif;
    private Boolean skipBarcodes;
    private Boolean useBarcodeAsId;
    private Boolean appendBarcode;
    private Integer lastDaysCashDocument;
    private boolean useShiftNumberAsNumberZReport;
    private boolean exportTaxes;
    private boolean zeroPaymentForZeroSumReceipt; //при 100% скидке создаём платёж с нулевой суммой (для hoddabi)
    private Boolean sendZeroQuantityForWeightItems;

    public UKM4MySQLSettings() {
    }

    public String getCashPayments() {
        return cashPayments;
    }

    public void setCashPayments(String cashPayments) {
        this.cashPayments = cashPayments;
    }

    public String getCardPayments() {
        return cardPayments;
    }

    public void setCardPayments(String cardPayments) {
        this.cardPayments = cardPayments;
    }

    public String getGiftCardPayments() {
        return giftCardPayments;
    }

    public void setGiftCardPayments(String giftCardPayments) {
        this.giftCardPayments = giftCardPayments;
    }

    public List<String> getGiftCardList() {
        return giftCardList;
    }

    public void setGiftCardsList(String giftCards) {
        giftCardList = new ArrayList<>();
        if(!giftCards.isEmpty())
            giftCardList.addAll(Arrays.asList(giftCards.split(",\\s?")));
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getSkipItems() {
        return skipItems;
    }

    public void setSkipItems(Boolean skipItems) {
        this.skipItems = skipItems;
    }

    public Boolean getSkipBarcodes() {
        return skipBarcodes;
    }

    public void setSkipBarcodes(Boolean skipBarcodes) {
        this.skipBarcodes = skipBarcodes;
    }

    public Boolean getUseBarcodeAsId() {
        return useBarcodeAsId;
    }

    public void setUseBarcodeAsId(Boolean useBarcodeAsId) {
        this.useBarcodeAsId = useBarcodeAsId;
    }

    public Boolean getAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(Boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public Boolean getSkipClassif() {
        return skipClassif;
    }

    public void setSkipClassif(Boolean skipClassif) {
        this.skipClassif = skipClassif;
    }

    public Integer getLastDaysCashDocument() {
        return lastDaysCashDocument;
    }

    public void setLastDaysCashDocument(Integer lastDaysCashDocument) {
        this.lastDaysCashDocument = lastDaysCashDocument;
    }

    public boolean isUseShiftNumberAsNumberZReport() {
        return useShiftNumberAsNumberZReport;
    }

    public void setUseShiftNumberAsNumberZReport(boolean useShiftNumberAsNumberZReport) {
        this.useShiftNumberAsNumberZReport = useShiftNumberAsNumberZReport;
    }

    public boolean isExportTaxes() {
        return exportTaxes;
    }

    public void setExportTaxes(boolean exportTaxes) {
        this.exportTaxes = exportTaxes;
    }

    public boolean isZeroPaymentForZeroSumReceipt() {
        return zeroPaymentForZeroSumReceipt;
    }

    public void setZeroPaymentForZeroSumReceipt(boolean zeroPaymentForZeroSumReceipt) {
        this.zeroPaymentForZeroSumReceipt = zeroPaymentForZeroSumReceipt;
    }

    public Boolean getSendZeroQuantityForWeightItems() {
        return sendZeroQuantityForWeightItems;
    }

    public void setSendZeroQuantityForWeightItems(Boolean sendZeroQuantityForWeightItems) {
        this.sendZeroQuantityForWeightItems = sendZeroQuantityForWeightItems;
    }
}
