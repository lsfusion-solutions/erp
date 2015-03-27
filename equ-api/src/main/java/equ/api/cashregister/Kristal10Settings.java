package equ.api.cashregister;

import java.io.Serializable;

public class Kristal10Settings implements Serializable{

    private Boolean brandIsManufacturer;
    private Boolean seasonIsCountry;
    private Boolean idItemInMarkingOfTheGood;
    private Boolean useShopIndices;

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
}
