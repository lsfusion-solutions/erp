package equ.clt.handler.cas;

import java.io.Serializable;

public class CASSettings implements Serializable{
    //если задано, умножаем цену при выгрузке на priceMultiplier,
    private Integer priceMultiplier;

    //если true, то в departmentNumber пишем 0, а в barcode number - weightCode для весового товара и pieceCode для штучного
    private boolean useWeightCodeInBarcodeNumber;

    public CASSettings() {}

    public Integer getPriceMultiplier() {
        return priceMultiplier;
    }

    public void setPriceMultiplier(Integer priceMultiplier) {
        this.priceMultiplier = priceMultiplier;
    }

    public boolean isUseWeightCodeInBarcodeNumber() {
        return useWeightCodeInBarcodeNumber;
    }

    public void setUseWeightCodeInBarcodeNumber(boolean useWeightCodeInBarcodeNumber) {
        this.useWeightCodeInBarcodeNumber = useWeightCodeInBarcodeNumber;
    }
}
