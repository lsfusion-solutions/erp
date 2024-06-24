package equ.clt.handler.mertech;

import java.io.Serializable;

public class MertechSettings implements Serializable{
    
    //Путь в JSON файлу приоритетных структур штрихкодов
    private String barcodeStructureFile;
    private Integer labelTemplate;
    private Integer labelDiscountTemplate;
    private Integer wrappingType;
    private String barcodePrefixType;
    
    public MertechSettings() {
    }

    public String getBarcodeStructureFile() {
        return barcodeStructureFile;
    }
    public void setBarcodeStructureFile(String barcodeStructureFile) {
        this.barcodeStructureFile = barcodeStructureFile;
    }
    
    public Integer getLabelTemplate() {
        return labelTemplate;
    }
    
    public void setLabelTemplate(Integer labelTemplate) {
        this.labelTemplate = labelTemplate;
    }
    
    public Integer getLabelDiscountTemplate() {
        return labelDiscountTemplate;
    }
    
    public void setLabelDiscountTemplate(Integer labelDiscountTemplate) {
        this.labelDiscountTemplate = labelDiscountTemplate;
    }
    
    public Integer getWrappingType() {
        return wrappingType;
    }
    
    public void setWrappingType(Integer wrappingType) {
        this.wrappingType = wrappingType;
    }
    
    public String getBarcodePrefixType() {
        return barcodePrefixType;
    }
    
    public void setBarcodePrefixType(String barcodePrefixType) {
        this.barcodePrefixType = barcodePrefixType;
    }
}