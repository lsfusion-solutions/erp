package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;

public class SalesInfo implements Serializable {
    public boolean isGiftCard;
    public Integer nppGroupMachinery;
    public Integer nppMachinery;
    public String numberZReport;
    public Integer numberReceipt;
    public Date dateReceipt;
    public Time timeReceipt;
    public String idEmployee;
    public String firstNameContact;
    public String lastNameContact;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public BigDecimal sumGiftCard;
    public String barcodeItem;
    public String idItem;
    public Integer itemObject;
    public BigDecimal quantityReceiptDetail;
    public BigDecimal priceReceiptDetail;
    public BigDecimal sumReceiptDetail;
    public BigDecimal discountSumReceiptDetail;
    public BigDecimal discountSumReceipt;
    public String seriesNumberDiscountCard;
    public Integer numberReceiptDetail;
    public String filename;
    public String idSection;

    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Integer numberReceipt,
                     Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact, String lastNameContact,
                     BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumGiftCard, String barcodeItem, String idItem,
                     Integer itemObject, BigDecimal quantityReceiptDetail, BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail,
                     BigDecimal discountSumReceiptDetail, BigDecimal discountSumReceipt, String seriesNumberDiscountCard, 
                     Integer numberReceiptDetail, String filename, String idSection) {
        this.isGiftCard = isGiftCard;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nppMachinery = nppMachinery;
        this.numberZReport = numberZReport;
        this.numberReceipt = numberReceipt;
        this.dateReceipt = dateReceipt;
        this.timeReceipt = timeReceipt;
        this.idEmployee = idEmployee;
        this.firstNameContact = firstNameContact;
        this.lastNameContact = lastNameContact;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumGiftCard = sumGiftCard;
        this.barcodeItem = barcodeItem;
        this.idItem = idItem;
        this.itemObject = itemObject;
        this.quantityReceiptDetail = quantityReceiptDetail;
        this.priceReceiptDetail = priceReceiptDetail;
        this.sumReceiptDetail = sumReceiptDetail;
        this.discountSumReceiptDetail = discountSumReceiptDetail;
        this.discountSumReceipt = discountSumReceipt;
        this.seriesNumberDiscountCard = seriesNumberDiscountCard;
        this.numberReceiptDetail = numberReceiptDetail;
        this.filename = filename;
        this.idSection = idSection;
    }

    //startDate - для обратной совместимости
    public String getIdZReport(Date startDate) {
        if (startDate == null || startDate.compareTo(dateReceipt) <= 0)
            return nppGroupMachinery + "_" + nppMachinery + "_" + numberZReport + "_" + new SimpleDateFormat("ddMMyyyy").format(dateReceipt);
        else
            return nppGroupMachinery + "_" + nppMachinery + "_" + numberZReport;
    }
    
    public String getIdReceipt(Date startDate) {
        return getIdZReport(startDate) + "_" + numberReceipt;
    }

    public String getIdReceiptDetail(Date startDate) {
        return getIdReceipt(startDate) + "_" + numberReceiptDetail;
    }
}
