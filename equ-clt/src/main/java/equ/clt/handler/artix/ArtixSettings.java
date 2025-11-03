package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    //Директория, в которую выгружаются файлы pos.aif, pos.flz (sendDiscountCardList, sendCashierInfoList).
    //Если не задана, указанные методы не работают
    private String globalExchangeDirectory;

    //Если true, то дополнительно копируем файлы pos.aif в globalExchangeDirectory (sendTransaction, sendStopList)
    private boolean copyPosToGlobalExchangeDirectory;

    //Если true, то при отправке обрезаем контрольный символ штрихкода, при получении добавляем
    private boolean appendBarcode;

    //временная опция для того, чтобы выгружать в barcodes штрихкод и с appendBarcode, и без
    private boolean doubleBarcodes;

    //Если не задано, успешно принятые файлы реализации копируются в подпапку success
    private boolean disableCopyToSuccess;

    //удаляем из папок подпапки success с успешно принятыми файлами с lastModified старше заданного кол-ва дней
    //по умолчанию - 7 дней
    private int cleanOldFilesDays = 7;

    //Если true, включается метод readCashDocuments
    private boolean readCashDocuments;

    //Если true, в sendDiscountCardList выгружаются записи client
    private boolean exportClients;

    //Если true, в sendTransaction выгружается искуственный товар для мягких чеков
    private boolean exportSoftCheckItem;

    //Максимальное количество считываемых файлов реализации. Если не задано, берутся все найденные файлы
    private Integer maxFilesCount;

    //Если true, появляется оплата "бонусами", надо считать сумму и % скидки вручную (введено для Fancy)
    private boolean bonusesInDiscountPositions;

    //Если true, цена сертификата берётся из certificatePositions (введено для Ostrov)
    private boolean giftCardPriceInCertificatePositions;

    //Если true, прочитанные непустые файлы без реализации и cashierTime не удаляются
    private boolean notDeleteEmptyFiles;

    //Если true, отключается работа с мягкими чеками
    private boolean disableSoftCheck;

    //Коды признака оплаты наличными через запятую. По умолчанию используется код 1
    private String cashPayments;
    //Коды признака оплаты картой через запятую. По умолчанию используется код 4
    private String cardPayments;
    //Коды признака оплаты подарочным сертификатом через запятую. По умолчанию используется код 6
    private String giftCardPayments;
    //Коды признака оплаты кастомными типами платежей через запятую.
    private String customPayments;
    //Коды признака оплаты "Оплати" через запятую.
    private String oplatiPayments;

    //время ожидания в секундах обработки транзакции после выгрузки, по умолчанию - 180 секунд
    private Integer timeout;

    //тип заполнения поля externalSum. 0 (по умолчанию) = sumGain, 1 = sumProtectedEnd - sumProtectedBeg – sumBack
    private int externalSumType;

    //работа с дополнительными данными для аптек
    private boolean medicineMode;

    //Если true, записываем identifier для чека продажи и sourceIdentifier для чека возврата в externalNumber
    private boolean receiptIdentifiersToExternalNumber;

    //Если true, при выгрузке кассиров в поле rank пишем namePosition вместо idPosition
    private boolean useNamePositionInRankCashier;

    //Если true, не выполняем запросы "Загрузить данные о кассирах" (рассчитываем, что выполнится аналогичный код в lsf)
    private boolean ignoreCashierInfoRequests;

    //временная опция для включения новой схемы medicineMode
    private boolean medicineModeNewScheme;

    //для заполнения обязательных полей для российских настроек
    private boolean russian;

    //дополнять код кассира кодом группы касс
    private boolean appendCashierId;

    //если true, то в качестве идентификатора товара используется idBarcode, а не idItem
    private boolean useBarcodeAsId;

    private boolean useBarcodeAsIdSpecialMode;

    //pattern, по которому игнорируются дисконтные карты в чеках
    private String ignoreDiscountCardPattern;

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    @SuppressWarnings("unused")
    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isCopyPosToGlobalExchangeDirectory() {
        return copyPosToGlobalExchangeDirectory;
    }

    public void setCopyPosToGlobalExchangeDirectory(boolean copyPosToGlobalExchangeDirectory) {
        this.copyPosToGlobalExchangeDirectory = copyPosToGlobalExchangeDirectory;
    }

    public boolean isAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public boolean isDoubleBarcodes() {
        return doubleBarcodes;
    }

    public void setDoubleBarcodes(boolean doubleBarcodes) {
        this.doubleBarcodes = doubleBarcodes;
    }

    public boolean isDisableCopyToSuccess() {
        return disableCopyToSuccess;
    }

    public void setDisableCopyToSuccess(boolean disableCopyToSuccess) {
        this.disableCopyToSuccess = disableCopyToSuccess;
    }

    public int getCleanOldFilesDays() {
        return cleanOldFilesDays;
    }

    public void setCleanOldFilesDays(int cleanOldFilesDays) {
        this.cleanOldFilesDays = cleanOldFilesDays;
    }

    public boolean isReadCashDocuments() {
        return readCashDocuments;
    }

    public void setReadCashDocuments(boolean readCashDocuments) {
        this.readCashDocuments = readCashDocuments;
    }

    public boolean isExportClients() {
        return exportClients;
    }

    public void setExportClients(boolean exportClients) {
        this.exportClients = exportClients;
    }

    public boolean isExportSoftCheckItem() {
        return exportSoftCheckItem;
    }

    public void setExportSoftCheckItem(boolean exportSoftCheckItem) {
        this.exportSoftCheckItem = exportSoftCheckItem;
    }

    public Integer getMaxFilesCount() {
        return maxFilesCount;
    }

    public void setMaxFilesCount(Integer maxFilesCount) {
        this.maxFilesCount = maxFilesCount;
    }

    public boolean isBonusesInDiscountPositions() {
        return bonusesInDiscountPositions;
    }

    public void setBonusesInDiscountPositions(boolean bonusesInDiscountPositions) {
        this.bonusesInDiscountPositions = bonusesInDiscountPositions;
    }

    public boolean isGiftCardPriceInCertificatePositions() {
        return giftCardPriceInCertificatePositions;
    }

    public void setGiftCardPriceInCertificatePositions(boolean giftCardPriceInCertificatePositions) {
        this.giftCardPriceInCertificatePositions = giftCardPriceInCertificatePositions;
    }

    public boolean isNotDeleteEmptyFiles() {
        return notDeleteEmptyFiles;
    }

    @SuppressWarnings("unused")
    public void setNotDeleteEmptyFiles(boolean notDeleteEmptyFiles) {
        this.notDeleteEmptyFiles = notDeleteEmptyFiles;
    }

    public boolean isDisableSoftCheck() {
        return disableSoftCheck;
    }

    public void setDisableSoftCheck(boolean disableSoftCheck) {
        this.disableSoftCheck = disableSoftCheck;
    }

    public String getCashPayments() {
        return cashPayments;
    }

    @SuppressWarnings("unused")
    public void setCashPayments(String cashPayments) {
        this.cashPayments = cashPayments;
    }

    public String getCardPayments() {
        return cardPayments;
    }

    @SuppressWarnings("unused")
    public void setCardPayments(String cardPayments) {
        this.cardPayments = cardPayments;
    }

    public String getGiftCardPayments() {
        return giftCardPayments;
    }

    @SuppressWarnings("unused")
    public void setGiftCardPayments(String giftCardPayments) {
        this.giftCardPayments = giftCardPayments;
    }

    public String getCustomPayments() {
        return customPayments;
    }

    public void setCustomPayments(String customPayments) {
        this.customPayments = customPayments;
    }

    public String getOplatiPayments() {
        return oplatiPayments;
    }

    @SuppressWarnings("unused")
    public void setOplatiPayments(String oplatiPayments) {
        this.oplatiPayments = oplatiPayments;
    }

    public Integer getTimeout() {
        return timeout != null ? timeout : 180;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public int getExternalSumType() {
        return externalSumType;
    }

    @SuppressWarnings("unused")
    public void setExternalSumType(int externalSumType) {
        this.externalSumType = externalSumType;
    }

    public boolean isMedicineMode() {
        return medicineMode;
    }

    @SuppressWarnings("unused")
    public void setMedicineMode(boolean medicineMode) {
        this.medicineMode = medicineMode;
    }

    public boolean isReceiptIdentifiersToExternalNumber() {
        return receiptIdentifiersToExternalNumber;
    }

    public void setReceiptIdentifiersToExternalNumber(boolean receiptIdentifiersToExternalNumber) {
        this.receiptIdentifiersToExternalNumber = receiptIdentifiersToExternalNumber;
    }

    public boolean isUseNamePositionInRankCashier() {
        return useNamePositionInRankCashier;
    }

    public void setUseNamePositionInRankCashier(boolean useNamePositionInRankCashier) {
        this.useNamePositionInRankCashier = useNamePositionInRankCashier;
    }

    public boolean isIgnoreCashierInfoRequests() {
        return ignoreCashierInfoRequests;
    }

    public void setIgnoreCashierInfoRequests(boolean ignoreCashierInfoRequests) {
        this.ignoreCashierInfoRequests = ignoreCashierInfoRequests;
    }

    public boolean isMedicineModeNewScheme() {
        return medicineModeNewScheme;
    }

    public void setMedicineModeNewScheme(boolean medicineModeNewScheme) {
        this.medicineModeNewScheme = medicineModeNewScheme;
    }

    public boolean isRussian() {
        return russian;
    }

    @SuppressWarnings("unused")
    public void setRussian(boolean russian) {
        this.russian = russian;
    }

    public boolean isAppendCashierId() {
        return appendCashierId;
    }

    public void setAppendCashierId(boolean appendCashierId) {
        this.appendCashierId = appendCashierId;
    }

    public Boolean getUseBarcodeAsId() {
        return useBarcodeAsId;
    }

    public void setUseBarcodeAsId(boolean useBarcodeAsId) {
        this.useBarcodeAsId = useBarcodeAsId;
    }

    public boolean isUseBarcodeAsIdSpecialMode() {
        return useBarcodeAsIdSpecialMode;
    }

    public void setUseBarcodeAsIdSpecialMode(boolean useBarcodeAsIdSpecialMode) {
        this.useBarcodeAsIdSpecialMode = useBarcodeAsIdSpecialMode;
    }

    public String getIgnoreDiscountCardPattern() {
        return ignoreDiscountCardPattern;
    }

    @SuppressWarnings("unused")
    public void setIgnoreDiscountCardPattern(String ignoreDiscountCardPattern) {
        this.ignoreDiscountCardPattern = ignoreDiscountCardPattern;
    }

    public boolean frDocNumToExternalNumber;

    public boolean isFrDocNumToExternalNumber() {
        return frDocNumToExternalNumber;
    }

    public void setFrDocNumToExternalNumber(boolean frDocNumToExternalNumber) {
        this.frDocNumToExternalNumber = frDocNumToExternalNumber;
    }
}