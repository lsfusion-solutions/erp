package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    //Директория, в которую выгружаются файлы pos.aif, pos.flz (sendDiscountCardList, sendCashierInfoList).
    //Если не задана, указанные методы не работают
    private String globalExchangeDirectory;

    //Если true, то при отправке обрезаем контрольный символ штрихкода, при получении добавляем
    private boolean appendBarcode;

    //Если не задано, успешно принятые файлы реализации копируются в подпапку success
    private boolean disableCopyToSuccess;

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

    //Если задано, считаем указанное количество первых папок, пришедших с сервера, приоритетными, файлы из них берём
    //в первую очередь. На сервере папки сортируются по priority 'Приоритет' = DATA INTEGER (GroupCashRegister); )
    private int priorityDirectoriesCount;

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

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public boolean isDisableCopyToSuccess() {
        return disableCopyToSuccess;
    }

    public void setDisableCopyToSuccess(boolean disableCopyToSuccess) {
        this.disableCopyToSuccess = disableCopyToSuccess;
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

    public void setNotDeleteEmptyFiles(boolean notDeleteEmptyFiles) {
        this.notDeleteEmptyFiles = notDeleteEmptyFiles;
    }

    public int getPriorityDirectoriesCount() {
        return priorityDirectoriesCount;
    }

    public void setPriorityDirectoriesCount(int priorityDirectoriesCount) {
        this.priorityDirectoriesCount = priorityDirectoriesCount;
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

    public String getOplatiPayments() {
        return oplatiPayments;
    }

    public void setOplatiPayments(String oplatiPayments) {
        this.oplatiPayments = oplatiPayments;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public int getExternalSumType() {
        return externalSumType;
    }

    public void setExternalSumType(int externalSumType) {
        this.externalSumType = externalSumType;
    }

    public boolean isMedicineMode() {
        return medicineMode;
    }

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
}