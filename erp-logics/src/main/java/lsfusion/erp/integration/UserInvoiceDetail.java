package lsfusion.erp.integration;


import java.math.BigDecimal;
import java.util.Date;

public class UserInvoiceDetail {
    public String idUserInvoice;
    public String series;
    public String number;
    public Boolean createPricing;
    public Boolean createShipment;
    public String idUserInvoiceDetail;
    public Date date;
    public String idItem;
    public Boolean isWare;
    public BigDecimal quantity;
    public String idSupplier;
    public String idCustomerStock;
    public String idSupplierStock;
    public BigDecimal price;
    public BigDecimal shipmentSum;
    public BigDecimal chargePrice;
    public BigDecimal manufacturingPrice;
    public BigDecimal wholesalePrice;
    public BigDecimal wholesaleMarkup;
    public BigDecimal retailPrice;
    public BigDecimal retailMarkup;
    public String certificateText;
    public String idContract;
    public String numberDeclaration;
    public Date dateDeclaration;
    public String numberCompliance;
    public Date fromDateCompliance;
    public Date toDateCompliance;
    public Date expiryDate;
    public String bin;
    public BigDecimal rateExchange;
    public BigDecimal homePrice;
    public BigDecimal priceDuty;
    public Boolean isHomeCurrency;
    public Boolean showDeclaration;
    public Boolean showManufacturingPrice;
    public String shortNameCurrency;
    public String codeCustomsGroup;
    public BigDecimal retailVAT;


    public UserInvoiceDetail(String idUserInvoice, String series, String number, Boolean createPricing,
                             Boolean createShipment, String idUserInvoiceDetail, Date date, String idItem, Boolean isWare,
                             BigDecimal quantity, String idSupplier, String idCustomerStock, String idSupplierStock,
                             BigDecimal price, BigDecimal shipmentSum, BigDecimal chargePrice, BigDecimal manufacturingPrice,
                             BigDecimal wholesalePrice, BigDecimal wholesaleMarkup, BigDecimal retailPrice,
                             BigDecimal retailMarkup, String certificateText, String idContract, String numberDeclaration,
                             Date dateDeclaration, String numberCompliance, Date fromDateCompliance, Date toDateCompliance,
                             Date expiryDate, String bin, BigDecimal rateExchange, BigDecimal homePrice, BigDecimal priceDuty,
                             Boolean isHomeCurrency, Boolean showDeclaration, Boolean showManufacturingPrice,
                             String shortNameCurrency, String codeCustomsGroup, BigDecimal retailVAT) {
        this.idUserInvoice = idUserInvoice;
        this.series = series;
        this.number = number;
        this.createPricing = createPricing;
        this.createShipment = createShipment;
        this.idUserInvoiceDetail = idUserInvoiceDetail;
        this.date = date;
        this.idItem = idItem;
        this.isWare = isWare;
        this.quantity = quantity;
        this.idSupplier = idSupplier;
        this.idCustomerStock = idCustomerStock;
        this.idSupplierStock = idSupplierStock;
        this.price = price;
        this.shipmentSum = shipmentSum;
        this.chargePrice = chargePrice;
        this.manufacturingPrice = manufacturingPrice;
        this.wholesalePrice = wholesalePrice;
        this.wholesaleMarkup = wholesaleMarkup;
        this.retailPrice = retailPrice;
        this.retailMarkup = retailMarkup;
        this.certificateText = certificateText;
        this.idContract = idContract;
        this.numberDeclaration = numberDeclaration;
        this.dateDeclaration = dateDeclaration;
        this.numberCompliance = numberCompliance;
        this.fromDateCompliance = fromDateCompliance;
        this.toDateCompliance = toDateCompliance;
        this.expiryDate = expiryDate;
        this.bin = bin;
        this.rateExchange = rateExchange;
        this.homePrice = homePrice;
        this.priceDuty = priceDuty;
        this.isHomeCurrency = isHomeCurrency;
        this.showDeclaration = showDeclaration;
        this.showManufacturingPrice = showManufacturingPrice;
        this.shortNameCurrency = shortNameCurrency;
        this.codeCustomsGroup = codeCustomsGroup;
        this.retailVAT = retailVAT;
    }
}
