package equ.clt.handler.kristal10;

import java.io.Serializable;
import java.util.*;

public class Kristal10Settings implements Serializable{

    //если true, то в sendTransaction для тега good создаётся подтег manufacturer
    //<manufacturer id=item.idbrand><name>item.nameBrand</name></manufacturer>
    private Boolean brandIsManufacturer;

    //если true, то в sendTransaction для тега good создаётся подтег country
    //<country id=item.idSeason><name>item.nameSeason</name></country>
    private Boolean seasonIsCountry;

    //если true, то в sendTransaction, sendStopListInfo и sendDeleteBarcodeInfo
    //для тега good в атрибут marking-of-the-good пишется item.idItem, а не штрихкод
    private Boolean idItemInMarkingOfTheGood;

    //если true, то в sendTransaction, sendStopListInfo и sendDeleteBarcodeInfo
    //при формировании штрихкода не добавляется весовой префикс
    private Boolean skipWeightPrefix;

    //если false, то в sendTransaction создаются plugin-property plu-number и composition
    private Boolean skipScalesInfo;

    //если true, то в sendTransaction, sendStopListInfo и sendDeleteBarcodeInfo
    //для тегов min-price-restriction, max-discount-restriction и для good добавляется shop-indices.
    //В readSalesInfo также учитывается shop-indices
    private Boolean useShopIndices;

    //если true, то в sendTransaction
    //для тегов min-price-restriction shop-indices не создаётся даже при включённом useShopIndices
    private Boolean skipUseShopIndicesMinPrice;

    //строка, которая добавляется в конец shopIndices
    private String weightShopIndices;

    //если true, то в readSalesInfo при поиске группы оборудования не учитываем overDepartNumber
    private Boolean ignoreSalesDepartmentNumber;

    //если true, то в sendTransaction
    //для тегов min-price-restriction, max-discount-restriction в тег id используется не barcodeItem, а idItem
    private Boolean useIdItemInRestriction;

    //если true, то в readCashDocumentInfo, readSalesInfo, readExtraCheckZReport
    //игнорируем проверку, заблокирован ли файл, прежде чем начать его читать
    private Boolean ignoreFileLock;

    //В readSalesInfo трансформирует UPC штрихкод
    //если равен "13to12", длина ШК = 13 и ШК начинается с "0", то отрезает этот "0"
    //если равен "12to13" и длина ШК = 12, добавляет в начало "0"
    private String transformUPCBarcode; //12to13 or 13to12

    //если задан, то при чтении файлов реализации берёт максимум maxFilesCount файлов за цикл
    private Integer maxFilesCount;

    //список групп через запятую, которым задаётся productType=ProductCiggyEntity
    private String tobaccoGroup;

    //если true, то в readSalesInfo
    //если длина ШК = 7 и ШК начинается на 2, отрезаем первые 2 символа.
    //если длина ШК = 13 и ШК начинается на 22 и символы с 8 по 13 != 00000 и
    //(кол-во не целое или кол-во совпадает с заданным в 7-12 символах ШК весом), то берём символы со 2 по 7.
    //если false, то в readSalesInfo
    //если длина ШК = 7 и ШК начинается на weightCode, отрезаем первые 2 символа.
    private Boolean ignoreSalesWeightPrefix;

    //удаляем из папок success успешно принятые файлы старше заданного кол-ва дней
    private Integer cleanOldFilesDays;

    //проценты по типам диксонтых карт, выгружаемые в sendDiscountCardList, вида 0->1, 3->2, 5->3, 10->4
    private String discountCardPercentType;
    private Map<Double, String> discountCardPercentTypeMap = new HashMap<>();

    //директория выгрузки файла catalog-cards, по умолчанию выгружается в /products/source/
    private String discountCardDirectory;

    //в sendTransaction, список префиксов штрихкодов
    //если длина ШК > 7 и ШК не начинается ни с одного из указанных префиксов,
    //то добавляем для штрихкода атрибут barcode-type=GTIN
    private String notGTINPrefixes;
    private List<String> notGTINPrefixesList = new ArrayList<>();

    //в readSalesInfo, если отсутствует plugin-property gift.card.number, но штрихкод соответствует указанной маске,
    //то формируем штрихкод как dateTimeReceipt/count,
    //где count начинается с 1 и используется для избежания дублирования штрихкодов. По умолчанию (?!666)\d{3}
    private String giftCardRegexp;

    //в readSalesInfo, sendTransaction, если true, то shopIndices = nppGroupMachinery, иначе - idDepartmentStore
    private Boolean useNumberGroupInShopIndices;

    //в sendTransaction, если true и задана item.section, то атрибут number для тега department из price-entry
    //берётся как первый элемент (до |) из списка section (через запятую).
    //иначе - transaction.departmentNumberGroupCashRegister
    //в readSalesInfo, если true, departNumber передаётся как section
    private Boolean useSectionAsDepartNumber;

    //в sendTransaction,
    //если задан, то сформированный файл catalog-goods дополнительно копируется по указанному sftp-пути
    //только для магазинов, заданных в sftpDepartmentStores (через запятую)
    private String sftpPath;
    private String sftpDepartmentStores;
    private List<String> sftpDepartmentStoresList = new ArrayList<>();

    public Kristal10Settings() {
    }

    public void setBrandIsManufacturer(Boolean brandIsManufacturer) {
        this.brandIsManufacturer = brandIsManufacturer;
    }

    public Boolean getBrandIsManufacturer() {
        return brandIsManufacturer;
    }

    public void setSeasonIsCountry(Boolean seasonIsCountry) {
        this.seasonIsCountry = seasonIsCountry;
    }

    public Boolean getSeasonIsCountry() {
        return seasonIsCountry;
    }

    public Boolean isIdItemInMarkingOfTheGood() {
        return idItemInMarkingOfTheGood;
    }

    public void setIdItemInMarkingOfTheGood(Boolean idItemInMarkingOfTheGood) {
        this.idItemInMarkingOfTheGood = idItemInMarkingOfTheGood;
    }

    public Boolean getSkipWeightPrefix() {
        return skipWeightPrefix;
    }

    public void setSkipWeightPrefix(Boolean skipWeightPrefix) {
        this.skipWeightPrefix = skipWeightPrefix;
    }

    public Boolean getSkipScalesInfo() {
        return skipScalesInfo;
    }

    public void setSkipScalesInfo(Boolean skipScalesInfo) {
        this.skipScalesInfo = skipScalesInfo;
    }

    public Boolean getUseShopIndices() {
        return useShopIndices;
    }

    public void setUseShopIndices(Boolean useShopIndices) {
        this.useShopIndices = useShopIndices;
    }

    public Boolean getSkipUseShopIndicesMinPrice() {
        return skipUseShopIndicesMinPrice;
    }

    public void setSkipUseShopIndicesMinPrice(Boolean skipUseShopIndicesMinPrice) {
        this.skipUseShopIndicesMinPrice = skipUseShopIndicesMinPrice;
    }

    public String getWeightShopIndices() {
        return weightShopIndices;
    }

    public void setWeightShopIndices(String weightShopIndices) {
        this.weightShopIndices = weightShopIndices;
    }

    public Boolean getIgnoreSalesDepartmentNumber() {
        return ignoreSalesDepartmentNumber;
    }

    public void setIgnoreSalesDepartmentNumber(Boolean ignoreSalesDepartmentNumber) {
        this.ignoreSalesDepartmentNumber = ignoreSalesDepartmentNumber;
    }

    public Boolean getUseIdItemInRestriction() {
        return useIdItemInRestriction;
    }

    public void setUseIdItemInRestriction(Boolean useIdItemInRestriction) {
        this.useIdItemInRestriction = useIdItemInRestriction;
    }

    public Boolean getIgnoreFileLock() {
        return ignoreFileLock;
    }

    public void setIgnoreFileLock(Boolean ignoreFileLock) {
        this.ignoreFileLock = ignoreFileLock;
    }

    public String getTransformUPCBarcode() {
        return transformUPCBarcode;
    }

    public void setTransformUPCBarcode(String transformUPCBarcode) {
        this.transformUPCBarcode = transformUPCBarcode;
    }

    public Integer getMaxFilesCount() {
        return maxFilesCount;
    }

    public void setMaxFilesCount(Integer maxFilesCount) {
        this.maxFilesCount = maxFilesCount;
    }

    public String getTobaccoGroup() {
        return tobaccoGroup;
    }

    public void setTobaccoGroup(String tobaccoGroup) {
        this.tobaccoGroup = tobaccoGroup;
    }

    public String getDiscountCardPercentType() {
        return discountCardPercentType;
    }

    public void setDiscountCardPercentType(String discountCardPercentType) {
        this.discountCardPercentType = discountCardPercentType;
        this.discountCardPercentTypeMap = new HashMap<>();
        if(!discountCardPercentType.isEmpty()) {
            String[] entries = discountCardPercentType.split(",\\s?");
            for (String entry : entries) {
                String[] percentType = entry.split("->");
                if(percentType.length == 2) {
                    try {
                        this.discountCardPercentTypeMap.put(Double.parseDouble(percentType[0]), percentType[1]);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public Map<Double, String> getDiscountCardPercentTypeMap() {
        return discountCardPercentTypeMap;
    }

    public void setDiscountCardPercentTypeMap(String discountCardPercentType) {
    }

    public Boolean getIgnoreSalesWeightPrefix() {
        return ignoreSalesWeightPrefix;
    }

    public void setIgnoreSalesWeightPrefix(Boolean ignoreSalesWeightPrefix) {
        this.ignoreSalesWeightPrefix = ignoreSalesWeightPrefix;
    }

    public Integer getCleanOldFilesDays() {
        return cleanOldFilesDays;
    }

    public void setCleanOldFilesDays(Integer cleanOldFilesDays) {
        this.cleanOldFilesDays = cleanOldFilesDays;
    }

    public String getDiscountCardDirectory() {
        return discountCardDirectory;
    }

    public void setDiscountCardDirectory(String discountCardDirectory) {
        this.discountCardDirectory = discountCardDirectory;
    }

    public List<String> getNotGTINPrefixesList() {
        return notGTINPrefixesList;
    }

    public void setNotGTINPrefixes(String notGTINPrefixes) {
        this.notGTINPrefixes = notGTINPrefixes;
        this.notGTINPrefixesList.addAll(Arrays.asList(notGTINPrefixes.split(",\\s?")));
    }

    public String getGiftCardRegexp() {
        return giftCardRegexp;
    }

    public void setGiftCardRegexp(String giftCardRegexp) {
        this.giftCardRegexp = giftCardRegexp;
    }

    public boolean useNumberGroupInShopIndices() {
        return useNumberGroupInShopIndices != null && useNumberGroupInShopIndices;
    }

    public void setUseNumberGroupInShopIndices(Boolean useNumberGroupInShopIndices) {
        this.useNumberGroupInShopIndices = useNumberGroupInShopIndices;
    }

    public Boolean getUseSectionAsDepartNumber() {
        return useSectionAsDepartNumber;
    }

    public void setUseSectionAsDepartNumber(Boolean useSectionAsDepartNumber) {
        this.useSectionAsDepartNumber = useSectionAsDepartNumber;
    }

    public String getSftpPath() {
        return sftpPath;
    }

    public void setSftpPath(String sftpPath) {
        this.sftpPath = sftpPath;
    }

    public List<String> getSftpDepartmentStoresList() {
        return sftpDepartmentStoresList;
    }

    public void setSftpDepartmentStores(String sftpDepartmentStores) {
        this.sftpDepartmentStores = sftpDepartmentStores;
        this.sftpDepartmentStoresList.addAll(Arrays.asList(sftpDepartmentStores.split(",\\s?")));
    }
}
