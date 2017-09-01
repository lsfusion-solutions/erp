package lsfusion.erp.region.by.certificate.declaration;


import java.math.BigDecimal;

public class DeclarationDetail {
    
    public Integer number;
    public String codeCustomsGroup;
    public String nameCustoms;
    public BigDecimal quantity;
    public BigDecimal sumNetWeight;
    public BigDecimal sumGrossWeight;
    public String shortNameUOM;
    public String codeUOM;
    public String sidOrigin2Country;
    public BigDecimal sum;
    public BigDecimal homeSum;
    public BigDecimal baseVATSum;
    public Boolean isWeightDuty;
    public BigDecimal weightDuty;
    public BigDecimal percentDuty;
    public BigDecimal percentVAT;
    public BigDecimal dutySum;
    public BigDecimal VATSum;
    public Boolean isVATCustomsException;
    public Long VATCustomsException;
    public BigDecimal componentsQuantity;
    public String extraName;
    public String markin;
    public String nameSupplier;
    public String nameBrand;
    public String nameManufacturer;
    
    public DeclarationDetail(Integer number, String codeCustomsGroup, String nameCustoms, BigDecimal quantity, 
                             BigDecimal sumNetWeight, BigDecimal sumGrossWeight, String shortNameUOM, String codeUOM, 
                             String sidOrigin2Country, BigDecimal sum, BigDecimal homeSum, BigDecimal baseVATSum,
                             Boolean isWeightDuty, BigDecimal weightDuty, BigDecimal percentDuty, BigDecimal percentVAT,
                             BigDecimal dutySum, BigDecimal VATSum, Boolean isVATCustomsException, Long VATCustomsException, 
                             BigDecimal componentsQuantity, String extraName, String markin, 
                             String nameSupplier, String nameBrand, String nameManufacturer) {
        this.number = number;
        this.codeCustomsGroup = codeCustomsGroup;
        this.nameCustoms = upper(nameCustoms);
        this.quantity = quantity;
        this.sumNetWeight = sumNetWeight;
        this.sumGrossWeight = sumGrossWeight;
        this.shortNameUOM = upper(shortNameUOM);
        this.codeUOM = codeUOM;
        this.sidOrigin2Country = sidOrigin2Country;
        this.sum = sum;
        this.homeSum = homeSum;
        this.baseVATSum = baseVATSum;
        this.isWeightDuty = isWeightDuty;
        this.weightDuty = weightDuty;
        this.percentDuty = percentDuty;
        this.percentVAT = percentVAT;
        this.dutySum = dutySum;
        this.VATSum = VATSum;
        this.isVATCustomsException = isVATCustomsException;
        this.VATCustomsException = VATCustomsException;
        this.componentsQuantity = componentsQuantity;
        this.extraName = upper(extraName);
        this.markin = markin;
        this.nameSupplier = upper(nameSupplier);
        this.nameBrand = upper(nameBrand);
        this.nameManufacturer = upper(nameManufacturer);
    }

    protected String upper(String value) {
        return value == null ? null : value.toUpperCase();
    }
}
