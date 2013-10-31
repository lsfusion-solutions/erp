package lsfusion.erp.integration.universal;


import java.math.BigDecimal;
import java.util.Date;

public class PurchaseInvoiceDetail {
    public String numberUserInvoice;
    public Date dateUserInvoice;
    public String currencyUserInvoice;
    public String idUserInvoiceDetail;
    public String idBarcodeSku;
    public String idBatch;
    public String idItem;
    public String idItemGroup;
    public String tariffCodeItem;
    public String captionItem;
    public String idUOM;
    public String idManufacturer;
    public String nameCountry;
    public String nameOriginCountry;
    public String nameImportCountry;
    public String idCustomer;
    public String idCustomerStock;
    public BigDecimal quantity;
    public BigDecimal price;
    public BigDecimal sum;
    public BigDecimal valueVAT;
    public BigDecimal sumVAT;
    public BigDecimal invoiceSum;
    public BigDecimal manufacturingPrice;
    public String numberCompliance;
    public String numberDeclaration;
    public Date expiryDate;
    public String idPharmacyPriceGroup;
    public String seriesPharmacy;
    public String idArticle;
    public String captionArticle;
    public String idColor;
    public String nameColor;
    public String idCollection;
    public String nameCollection;
    public String idSize;
    public String nameSize;
    public String idSeasonYear;
    public String idSeason;
    public String nameSeason;
    public String idTheme;
    public String nameTheme;
    public BigDecimal netWeight;
    public BigDecimal grossWeight;
    public String composition;


    public PurchaseInvoiceDetail(String numberUserInvoice, Date dateUserInvoice, String currencyUserInvoice, 
                                 String idUserInvoiceDetail, String idBarcodeSku, String idBatch, String idItem, 
                                 String idItemGroup, String tariffCodeItem, String captionItem, String idUOM, 
                                 String idManufacturer, String nameCountry, String nameOriginCountry, 
                                 String nameImportCountry, String idCustomer, String idCustomerStock, BigDecimal quantity,
                                 BigDecimal price, BigDecimal sum, BigDecimal valueVAT, BigDecimal sumVAT, 
                                 BigDecimal invoiceSum, BigDecimal manufacturingPrice, String numberCompliance, 
                                 String numberDeclaration, Date expiryDate, String idPharmacyPriceGroup, 
                                 String seriesPharmacy, String idArticle, String captionArticle, String idColor,
                                 String nameColor, String idCollection, String nameCollection, String idSize, 
                                 String nameSize, String idSeasonYear, String idSeason, String nameSeason, 
                                 String idTheme, String nameTheme, BigDecimal netWeight, BigDecimal grossWeight, 
                                 String composition) {
        this.numberUserInvoice = numberUserInvoice;
        this.dateUserInvoice = dateUserInvoice;
        this.currencyUserInvoice = currencyUserInvoice;
        this.idUserInvoiceDetail = idUserInvoiceDetail;
        this.idBarcodeSku = idBarcodeSku;
        this.idBatch = idBatch;
        this.idItem = idItem;
        this.idItemGroup = idItemGroup;
        this.tariffCodeItem = tariffCodeItem;
        this.captionItem = captionItem;
        this.idUOM = idUOM;
        this.idManufacturer = idManufacturer;
        this.nameCountry = nameCountry;
        this.nameOriginCountry = nameOriginCountry;
        this.nameImportCountry = nameImportCountry;
        this.idCustomer = idCustomer;
        this.idCustomerStock = idCustomerStock;
        this.quantity = quantity;
        this.price = price;
        this.sum = sum;
        this.valueVAT = valueVAT;
        this.sumVAT = sumVAT;
        this.invoiceSum = invoiceSum;
        this.manufacturingPrice = manufacturingPrice;
        this.numberCompliance = numberCompliance;
        this.numberDeclaration = numberDeclaration;
        this.expiryDate = expiryDate;
        this.idPharmacyPriceGroup = idPharmacyPriceGroup;
        this.seriesPharmacy = seriesPharmacy;
        this.idArticle = idArticle;
        this.captionArticle = captionArticle;
        this.idColor = idColor;
        this.nameColor = nameColor;
        this.idCollection = idCollection;
        this.nameCollection = nameCollection;
        this.idSize = idSize;
        this.nameSize = nameSize;
        this.idSeasonYear = idSeasonYear;
        this.idSeason = idSeason;
        this.nameSeason = nameSeason;
        this.idTheme = idTheme;
        this.nameTheme = nameTheme;
        this.netWeight = netWeight;
        this.grossWeight = grossWeight;
        this.composition = composition;
    }
}
