package equ.clt.handler.cas;

import java.io.Serializable;

public class CL5000JSettings implements Serializable{

    private Integer priceCoefficient;

    public CL5000JSettings() {
    }

    public Integer getPriceCoefficient() {
        return priceCoefficient;
    }

    public void setPriceCoefficient(Integer priceCoefficient) {
        this.priceCoefficient = priceCoefficient;
    }
}