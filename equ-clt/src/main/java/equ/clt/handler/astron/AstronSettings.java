package equ.clt.handler.astron;

import java.io.Serializable;

public class AstronSettings implements Serializable {

    //время ожидания в секундах обработки транзакции после выгрузки, по умолчанию - 300 секунд
    private Integer timeout;

    //номера платежей наличными через запятую, по умолчанию - 0
    private String cashPayments;

    //номера платежей карточкой через запятую, по умолчанию - 1
    private String cardPayments;

    //номера платежей подарочными сертификатами через запятую, по умолчанию - 2
    private String giftCardPayments;

    //Коды признака оплаты кастомными типами платежей через запятую.
    private String customPayments;

    //если true, то игнорируем продажи, для которых не найдена касса
    private boolean ignoreSalesInfoWithoutCashRegister;

    //если true, то выгружаем таблицы prclevel, sarea, sareaprc
    private boolean exportExtraTables;

    //если задано и больше 0, выгружаем транзакции блоками по transactionsAtATime за раз,
    //иначе - по одной
    private Integer transactionsAtATime;

    //если задано и больше 0, выгружаем суммарно транзакций на itemsAtATime товаров за блок
    //в сочетании с transactionsAtATime берётся минимальное кол-во транзакций
    private Integer itemsAtATime;

    //если задано и больше 0, разбиваем выгрузку каждой таблицы на блоки по maxBatchSize
    private Integer maxBatchSize;

    //Опция для mssql - использовать режим выгрузки "на основе номера обновления"
    private boolean versionalScheme;

    //Выгружать deleteBarcode не вместе с со следующей выгрузкой, а отдельно
    private boolean deleteBarcodeInSeparateProcess;

    //если true, то выгружаем поле PROPERTYGRPID таблицы pack
    private boolean usePropertyGridFieldInPackTable;

    //если true, то в дополнение к таблице DCARD выгружаем таблицы CLNTGRP, CLNT, CLNTFORM, CLNTFORMITEMS, CLNTFORMPROPERTY
    private boolean exportDiscountCardExtraTables;

    //если true, то оплата "бонусами" (salesType = 2) считается как социальная скидка
    private boolean bonusPaymentAsDiscount;

    //если true, то в sql.log пишется всё, что читается во время readSales
    private boolean enableSqlLog;

    //если true, то при неверсионной схеме после выгрузки флагов проверяется не таблица DATAPUMP, а Syslog_DataServer
    private boolean waitSysLogInsteadOfDataPump;

    //если true, то влияет на поля PACKDTYPE и BARCID таблицы PACK
    private boolean specialSplitMode;

    //если true, меняем местами значения idVAT для 10 и 20
    private boolean swap10And20VAT;

    //новый запрос для readSales, если он будет работать, то выполнять новый по умолчанию
    private boolean newReadSalesQuery;

    //Не пытаться создать индексы в sales. если false т осоздаем
    private boolean skipCheckSalesIndex;

    public AstronSettings() {
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public boolean isExportExtraTables() {
        return exportExtraTables;
    }

    public void setExportExtraTables(boolean exportExtraTables) {
        this.exportExtraTables = exportExtraTables;
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

    public boolean isIgnoreSalesInfoWithoutCashRegister() {
        return ignoreSalesInfoWithoutCashRegister;
    }

    public void setIgnoreSalesInfoWithoutCashRegister(boolean ignoreSalesInfoWithoutCashRegister) {
        this.ignoreSalesInfoWithoutCashRegister = ignoreSalesInfoWithoutCashRegister;
    }

    public Integer getTransactionsAtATime() {
        return transactionsAtATime != null && transactionsAtATime > 1 ? transactionsAtATime : 1;
    }

    public void setTransactionsAtATime(Integer transactionsAtATime) {
        this.transactionsAtATime = transactionsAtATime;
    }

    public Integer getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(Integer maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public Integer getItemsAtATime() {
        return itemsAtATime != null ? itemsAtATime : 0;
    }

    @SuppressWarnings("unused")
    public void setItemsAtATime(Integer itemsAtATime) {
        this.itemsAtATime = itemsAtATime;
    }

    public boolean isVersionalScheme() {
        return versionalScheme;
    }

    public void setVersionalScheme(boolean versionalScheme) {
        this.versionalScheme = versionalScheme;
    }

    public boolean isDeleteBarcodeInSeparateProcess() {
        return deleteBarcodeInSeparateProcess;
    }

    public void setDeleteBarcodeInSeparateProcess(boolean deleteBarcodeInSeparateProcess) {
        this.deleteBarcodeInSeparateProcess = deleteBarcodeInSeparateProcess;
    }

    public boolean isUsePropertyGridFieldInPackTable() {
        return usePropertyGridFieldInPackTable;
    }

    public void setUsePropertyGridFieldInPackTable(boolean usePropertyGridFieldInPackTable) {
        this.usePropertyGridFieldInPackTable = usePropertyGridFieldInPackTable;
    }

    public boolean isExportDiscountCardExtraTables() {
        return exportDiscountCardExtraTables;
    }

    public void setExportDiscountCardExtraTables(boolean exportDiscountCardExtraTables) {
        this.exportDiscountCardExtraTables = exportDiscountCardExtraTables;
    }

    public boolean isBonusPaymentAsDiscount() {
        return bonusPaymentAsDiscount;
    }

    public void setBonusPaymentAsDiscount(boolean bonusPaymentAsDiscount) {
        this.bonusPaymentAsDiscount = bonusPaymentAsDiscount;
    }

    public boolean isEnableSqlLog() {
        return enableSqlLog;
    }

    public void setEnableSqlLog(boolean enableSqlLog) {
        this.enableSqlLog = enableSqlLog;
    }

    public boolean isWaitSysLogInsteadOfDataPump() {
        return waitSysLogInsteadOfDataPump;
    }

    public void setWaitSysLogInsteadOfDataPump(boolean waitSysLogInsteadOfDataPump) {
        this.waitSysLogInsteadOfDataPump = waitSysLogInsteadOfDataPump;
    }

    public boolean isSpecialSplitMode() {
        return specialSplitMode;
    }

    public void setSpecialSplitMode(boolean specialSplitMode) {
        this.specialSplitMode = specialSplitMode;
    }

    public boolean isSwap10And20VAT() {
        return swap10And20VAT;
    }

    public void setSwap10And20VAT(boolean swap10And20VAT) {
        this.swap10And20VAT = swap10And20VAT;
    }

    public boolean isNewReadSalesQuery() {
        return newReadSalesQuery;
    }

    public void setNewReadSalesQuery(boolean newReadSalesQuery) {
        this.newReadSalesQuery = newReadSalesQuery;
    }

    public boolean isSkipCheckSalesIndex() {
        return skipCheckSalesIndex;
    }

    public void setSkipCheckSalesIndex(boolean skipCheckSalesIndex) {
        this.skipCheckSalesIndex = skipCheckSalesIndex;
    }
}