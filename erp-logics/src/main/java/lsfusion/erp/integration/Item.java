package lsfusion.erp.integration;


import java.math.BigDecimal;
import java.time.LocalDate;

public class Item {
    public String idItem;
    public String idItemGroup;
    public String captionItem;
    public String idUOM;
    public String nameBrand;
    public String idBrand;
    public String nameCountry;
    public String idBarcode;
    public String extIdBarcode;
    public LocalDate date;
    public Boolean splitItem;
    public BigDecimal netWeightItem;
    public BigDecimal grossWeightItem;
    public String compositionItem;
    public BigDecimal retailVAT;
    public String idWare;
    public BigDecimal priceWare;
    public BigDecimal vatWare;
    public String idWriteOffRate;
    public BigDecimal baseMarkup;
    public BigDecimal retailMarkup;
    public String idBarcodePack;
    public BigDecimal amountPack;
    public String idUOMPack;
    public String idManufacturer;
    public String nameManufacturer;
    public String codeCustomsGroup;
    public String nameCustomsZone;

    public Item(String idItem, String idItemGroup, String captionItem, String idUOM, String nameBrand, String idBrand,
                String nameCountry, String idBarcode, String extIdBarcode, LocalDate date, Boolean splitItem,
                BigDecimal netWeightItem, BigDecimal grossWeightItem, String compositionItem, BigDecimal retailVAT,
                String idWare, BigDecimal priceWare, BigDecimal vatWare, String idWriteOffRate, BigDecimal baseMarkup,
                BigDecimal retailMarkup, String idBarcodePack, BigDecimal amountPack, String idUOMPack, 
                String idManufacturer, String nameManufacturer, String codeCustomsGroup, String nameCustomsZone) {
        this.idItem = idItem;
        this.idItemGroup = idItemGroup;
        this.captionItem = captionItem;
        this.idUOM = idUOM;
        this.nameBrand = nameBrand;
        this.idBrand = idBrand;
        this.nameCountry = nameCountry;
        this.idBarcode = idBarcode;
        this.extIdBarcode = extIdBarcode;
        this.date = date;
        this.splitItem = splitItem;
        this.netWeightItem = netWeightItem;
        this.grossWeightItem = grossWeightItem;
        this.compositionItem = compositionItem;
        this.retailVAT = retailVAT;
        this.idWare = idWare;
        this.priceWare = priceWare;
        this.vatWare = vatWare;
        this.idWriteOffRate = idWriteOffRate;
        this.baseMarkup = baseMarkup;
        this.retailMarkup = retailMarkup;
        this.idBarcodePack = idBarcodePack;
        this.amountPack = amountPack;
        this.idUOMPack = idUOMPack;
        this.idManufacturer = idManufacturer;
        this.nameManufacturer = nameManufacturer;
        this.codeCustomsGroup = codeCustomsGroup;
        this.nameCustomsZone = nameCustomsZone;
    }
}
