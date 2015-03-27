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

    public boolean getBrandIsManufacturer() {
        return brandIsManufacturer != null && brandIsManufacturer;
    }

    public void setSeasonIsCountry(Boolean seasonIsCountry) {
        this.seasonIsCountry = seasonIsCountry;
    }

    public boolean getSeasonIsCountry() {
        return seasonIsCountry != null && seasonIsCountry;
    }

    public boolean isIdItemInMarkingOfTheGood() {
        return idItemInMarkingOfTheGood != null && idItemInMarkingOfTheGood;
    }

    public void setIdItemInMarkingOfTheGood(Boolean idItemInMarkingOfTheGood) {
        this.idItemInMarkingOfTheGood = idItemInMarkingOfTheGood;
    }

    public boolean getUseShopIndices() {
        return useShopIndices != null && useShopIndices;
    }

    public void setUseShopIndices(Boolean useShopIndices) {
        this.useShopIndices = useShopIndices;
    }
}
