package equ.clt.handler.astron;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class AstronSettings implements Serializable {

    //время ожидания в секундах обработки транзакции после выгрузки, по умолчанию - 300 секунд
    private Integer timeout;

    //отображение nppGroupMachiney на extGrpId (x -> y)
    public String groupMachineries = null;

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

    public AstronSettings() {
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Map<Integer, Integer> getGroupMachineryMap() {
        Map<Integer, Integer> groupMachineryMap = new HashMap<>();
        if(groupMachineries != null) {
            for (String groupMachinery : groupMachineries.split(",")) {
                String[] entry = trim(groupMachinery).split("->");
                if (entry.length == 2) {
                    Integer key = parseInt(trim(entry[0]));
                    Integer value = parseInt(trim(entry[1]));
                    if (key != null && value != null)
                        groupMachineryMap.put(key, value);
                }
            }
        }
        return groupMachineryMap;
    }

    public void setGroupMachineries(String groupMachineries) {
        this.groupMachineries = groupMachineries;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
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
}