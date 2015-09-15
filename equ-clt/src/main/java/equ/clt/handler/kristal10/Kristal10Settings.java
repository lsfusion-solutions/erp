package equ.clt.handler.kristal10;

import java.io.Serializable;

public class Kristal10Settings implements Serializable{

    private Boolean brandIsManufacturer;
    private Boolean seasonIsCountry;
    private Boolean idItemInMarkingOfTheGood;
    private Boolean useShopIndices;
    private String transformUPCBarcode; //12to13 or 13to12
    private Integer maxFilesCount;
    private String tobaccoGroup;
    private Boolean ignoreSalesWeightPrefix;
    private Integer cleanOldFilesDays;

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

    public Boolean getUseShopIndices() {
        return useShopIndices;
    }

    public void setUseShopIndices(Boolean useShopIndices) {
        this.useShopIndices = useShopIndices;
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
}
