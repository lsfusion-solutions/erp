package equ.api.cashregister;

import java.io.Serializable;

public class Kristal10Settings implements Serializable{

    public boolean brandIsManufacturer;
    public boolean seasonIsCountry;

    public Kristal10Settings(boolean brandIsManufacturer, boolean seasonIsCountry) {
        this.brandIsManufacturer = brandIsManufacturer;
        this.seasonIsCountry = seasonIsCountry;
    }
}
