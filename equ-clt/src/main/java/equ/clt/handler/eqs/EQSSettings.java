package equ.clt.handler.eqs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EQSSettings implements Serializable{

    //Если true, то при отправке обрезаем контрольный символ штрихкода, при получении добавляем
    private Boolean appendBarcode;

    //Если задано, при приёме реализации обнаруживаем продажу сертификатов путём сравнения штрихкода с regexp
    private String giftCardRegexp;

    //Если true, то перед выгрузкой таблицы plu делаем truncate (иначе - DELETE FROM plu WHERE store=idDepartmentStoreGroupCashRegister),
    //в поле store выгружаем пустую строку (иначе - idDepartmentStoreGroupCashRegister)
    private Boolean skipIdDepartmentStore;

    //список idDepartmentStoreGroupCashRegister через запятую. Если idDepartmentStore входит в список,
    //галочка skipIdDepartmentStore для него игнорируется
    private String forceIdDepartmentStores;
    private List<String> forceIdDepartmentStoresList = new ArrayList<>();

    //Количество потоков, на которые распараллеливается выгрузка дисконтных карт.
    //По умолчанию - каждое задание выполняется в отдельном потоке.
    private int discountCardThreadCount;

    public EQSSettings() {
    }

    public Boolean getAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(Boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public String getGiftCardRegexp() {
        return giftCardRegexp;
    }

    public void setGiftCardRegexp(String giftCardRegexp) {
        this.giftCardRegexp = giftCardRegexp;
    }

    public Boolean getSkipIdDepartmentStore() {
        return skipIdDepartmentStore;
    }

    public void setSkipIdDepartmentStore(Boolean skipIdDepartmentStore) {
        this.skipIdDepartmentStore = skipIdDepartmentStore;
    }

    public List<String> getForceIdDepartmentStoresList() {
        return forceIdDepartmentStoresList;
    }

    public void setForceIdDepartmentStores(String forceIdDepartmentStores) {
        this.forceIdDepartmentStores = forceIdDepartmentStores;
        this.forceIdDepartmentStoresList.addAll(Arrays.asList(forceIdDepartmentStores.split(",\\s?")));
    }

    public int getDiscountCardThreadCount() {
        return discountCardThreadCount;
    }

    public void setDiscountCardThreadCount(int discountCardThreadCount) {
        this.discountCardThreadCount = discountCardThreadCount;
    }
}