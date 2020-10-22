package equ.clt.handler.ukm4mysql;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UKM4MySQLSettings implements Serializable{

    //Коды признака оплаты наличными через запятую. По умолчанию используется код 0
    private String cashPayments;
    //Коды признака оплаты картой через запятую. По умолчанию используется код 1
    private String cardPayments;
    //Коды признака оплаты подарочным сертификатом через запятую. По умолчанию используется код 2
    private String giftCardPayments;
    //Коды признака оплаты кастомными типами платежей через запятую.
    private String customPayments;

    //Список подарочных сертификатов через запятую. Если receipt_payment.card_number содержится в списке,
    //тип оплаты считается подарочным сертификатом
    private List<String> giftCardList = new ArrayList<>();

    //время ожидания в секундах обработки транзакции после выгрузки, по умолчанию - 300 секунд
    private Integer timeout;

    //если true, то не выгружаются таблицы classif, taxes, taxGroups, items, itemsStocks, stocks, priceList,
    //priceType, priceTypeStorePriceList, var, properties, propertyValues, itemPropertyValues
    private Boolean skipItems;

    //если true, то не выгружается таблица classif
    private Boolean skipClassif;

    //если true, то не выгружается таблица priceListVar
    private Boolean skipBarcodes;

    //если true, то в качестве идентификатора товара используется idBarcode, а не idItem
    private Boolean useBarcodeAsId;

    //Если true, то при отправке обрезаем контрольный символ штрихкода, при получении добавляем
    private Boolean appendBarcode;

    //Если задано, то readCashDocumentInfo читает только CashDocument не старше указанного кол-ва дней.
    //По умолчанию читаются все CashDocument
    private Integer lastDaysCashDocument;

    //Если true, то номер z-отчёта читается из shift.number, иначе из receipt.shift_open
    private boolean useShiftNumberAsNumberZReport;

    //Если true, то выгружаем таблицы taxes и taxGroups
    private boolean exportTaxes;

    //Если true, то при 100% скидке создаём платёж с нулевой суммой (для hoddabi)
    private boolean zeroPaymentForZeroSumReceipt;

    //Если true, то для весовых товаров отправляем кол-во 0
    private Boolean sendZeroQuantityForWeightItems;

    //Если true, то касса ищется по store и номеру кассы, иначе - только по номеру
    private boolean cashRegisterByStoreAndNumber;

    //Список через запятую extId групп товаров, которые экспортируются в classif при каждой выгрузке
    private String forceGroups;
    private List<String> forceGroupsList = new ArrayList<>();

    //если true, то выгружаем в таблицу Var поле tare_weight
    private boolean tareWeightFieldInVarTable;

    //если true, то используем r.local_number, иначе r.global_number
    private boolean useLocalNumber;

    //если true, то idEmployee = store + "_" + user_id; иначе = user_id
    private boolean useStoreInIdEmployee;

    //если true, для идентификации кассы вместо cash_id используем cash_number
    private boolean useCashNumberInsteadOfCashId;

    //если true, то для штучных товаров (ед.изм = ШТ) используем pieceCode вместо weightCode
    private boolean usePieceCode;


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

    public String getCustomPayments() {
        return customPayments;
    }

    public void setCustomPayments(String customPayments) {
        this.customPayments = customPayments;
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

    public List<String> getForceGroupsList() {
        return forceGroupsList;
    }

    public void setForceGroups(String forceGroups) {
        this.forceGroups = forceGroups;
        this.forceGroupsList.addAll(Arrays.asList(forceGroups.split(",\\s?")));
    }

    public boolean isCashRegisterByStoreAndNumber() {
        return cashRegisterByStoreAndNumber;
    }

    public void setCashRegisterByStoreAndNumber(boolean cashRegisterByStoreAndNumber) {
        this.cashRegisterByStoreAndNumber = cashRegisterByStoreAndNumber;
    }

    public boolean isTareWeightFieldInVarTable() {
        return tareWeightFieldInVarTable;
    }

    public void setTareWeightFieldInVarTable(boolean tareWeightFieldInVarTable) {
        this.tareWeightFieldInVarTable = tareWeightFieldInVarTable;
    }

    public boolean isUseLocalNumber() {
        return useLocalNumber;
    }

    public void setUseLocalNumber(boolean useLocalNumber) {
        this.useLocalNumber = useLocalNumber;
    }

    public boolean isUseStoreInIdEmployee() {
        return useStoreInIdEmployee;
    }

    public void setUseStoreInIdEmployee(boolean useStoreInIdEmployee) {
        this.useStoreInIdEmployee = useStoreInIdEmployee;
    }

    public boolean isUseCashNumberInsteadOfCashId() {
        return useCashNumberInsteadOfCashId;
    }

    public void setUseCashNumberInsteadOfCashId(boolean useCashNumberInsteadOfCashId) {
        this.useCashNumberInsteadOfCashId = useCashNumberInsteadOfCashId;
    }

    public boolean isUsePieceCode() {
        return usePieceCode;
    }

    public void setUsePieceCode(boolean usePieceCode) {
        this.usePieceCode = usePieceCode;
    }
}
