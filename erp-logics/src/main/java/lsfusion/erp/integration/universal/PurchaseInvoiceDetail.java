package lsfusion.erp.integration.universal;


import java.math.BigDecimal;
import java.util.Date;

public class PurchaseInvoiceDetail {
    public String numberUserInvoice;
    public String idUserInvoiceDetail;
    public String idBarcodeSku;
    public String idBatch;
    public String idItem;
    public String captionItem;
    public String idUOM;
    public String idManufacturer;
    public String nameCountry;
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


    public PurchaseInvoiceDetail(String numberUserInvoice, String idUserInvoiceDetail, String idBarcodeSku,
                                 String idBatch, String idItem, String captionItem, String idUOM, String idManufacturer,
                                 String nameCountry, String nameImportCountry, String idCustomer, 
                                 String idCustomerStock, BigDecimal quantity, BigDecimal price, BigDecimal sum, 
                                 BigDecimal valueVAT, BigDecimal sumVAT, BigDecimal invoiceSum, 
                                 BigDecimal manufacturingPrice,  String numberCompliance, String numberDeclaration, 
                                 Date expiryDate, String idPharmacyPriceGroup, String seriesPharmacy) {
        this.numberUserInvoice = numberUserInvoice;
        this.idUserInvoiceDetail = idUserInvoiceDetail;
        this.idBarcodeSku = idBarcodeSku;
        this.idBatch = idBatch;
        this.idItem = idItem;
        this.captionItem = captionItem;
        this.idUOM = idUOM;
        this.idManufacturer = idManufacturer;
        this.nameCountry = nameCountry;
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
    }
}
