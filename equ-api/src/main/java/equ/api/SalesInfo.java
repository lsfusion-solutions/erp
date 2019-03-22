package equ.api;

import equ.api.cashregister.CashRegisterInfo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class SalesInfo implements Serializable {
    public ZReportInfo zReportInfo;
    public boolean isGiftCard;
    public boolean isReturnGiftCard;
    public Integer nppGroupMachinery;
    public Integer nppMachinery;
    public String numberZReport;
    public Date dateZReport;
    public Time timeZReport;
    public Integer numberReceipt;
    public Date dateReceipt;
    public Time timeReceipt;
    public String idEmployee;
    public String firstNameContact;
    public String lastNameContact;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public Map<String, GiftCard> sumGiftCardMap;
    public String barcodeItem;
    public String idItem;
    public Long itemObject;
    public String idSaleReceiptReceiptReturnDetail;
    public BigDecimal quantityReceiptDetail;
    public BigDecimal priceReceiptDetail;
    public BigDecimal sumReceiptDetail;
    public BigDecimal discountPercentReceiptDetail;
    public BigDecimal discountSumReceiptDetail;
    public BigDecimal discountSumReceipt;
    public String seriesNumberDiscountCard;
    public Integer numberReceiptDetail;
    public String filename;
    public String idSection;
    public boolean skipReceipt;
    public CashRegisterInfo cashRegisterInfo;

    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumGiftCard, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection) {
        this(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, (Map<String, GiftCard>) null/*sumGiftCardMap*/, barcodeItem, idItem, itemObject,
                idSaleReceiptReceiptReturnDetail, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail,
                discountSumReceipt, seriesNumberDiscountCard, numberReceiptDetail, filename, idSection);
        setSumGiftCardMap(sumGiftCard);
    }

    public SalesInfo(boolean isGiftCard, boolean isReturnGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumGiftCard, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountPercentReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection) {
        this(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, null, barcodeItem, idItem, itemObject,
                idSaleReceiptReceiptReturnDetail, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountPercentReceiptDetail, discountSumReceiptDetail,
                discountSumReceipt, seriesNumberDiscountCard, numberReceiptDetail, filename, idSection);
        setSumGiftCardMap(sumGiftCard);
        this.isReturnGiftCard = isReturnGiftCard;
    }

    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, BigDecimal sumGiftCard, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection, CashRegisterInfo cashRegisterInfo) {
        this(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, null, barcodeItem, idItem, itemObject,
                idSaleReceiptReceiptReturnDetail, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, null, discountSumReceiptDetail,
                discountSumReceipt, seriesNumberDiscountCard, numberReceiptDetail, filename, idSection, false, cashRegisterInfo);
        setSumGiftCardMap(sumGiftCard);
    }

    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, Map<String, GiftCard> sumGiftCardMap, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection) {
        this(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, sumGiftCardMap, barcodeItem, idItem, itemObject, idSaleReceiptReceiptReturnDetail,
                quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, null, discountSumReceiptDetail, discountSumReceipt, seriesNumberDiscountCard,
                numberReceiptDetail, filename, idSection, false, null);
    }

    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, Map<String, GiftCard> sumGiftCardMap, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountPercentReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection) {
        this(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, sumGiftCardMap, barcodeItem, idItem, itemObject, idSaleReceiptReceiptReturnDetail,
                quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountPercentReceiptDetail, discountSumReceiptDetail, discountSumReceipt, seriesNumberDiscountCard,
                numberReceiptDetail, filename, idSection, false, null);
    }

    //Artix, Kristal10, UKM4MySQL
    public SalesInfo(boolean isGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, Date dateZReport,
                     Time timeZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, Map<String, GiftCard> sumGiftCardMap, String barcodeItem,
                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountPercentReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection, boolean skipReceipt, CashRegisterInfo cashRegisterInfo) {
        this.isGiftCard = isGiftCard;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nppMachinery = nppMachinery;
        this.numberZReport = numberZReport;
        this.dateZReport = dateZReport;
        this.timeZReport = timeZReport;
        this.numberReceipt = numberReceipt;
        this.dateReceipt = dateReceipt;
        this.timeReceipt = timeReceipt;
        this.idEmployee = idEmployee;
        this.firstNameContact = firstNameContact;
        this.lastNameContact = lastNameContact;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumGiftCardMap = sumGiftCardMap;
        this.barcodeItem = barcodeItem;
        this.idItem = idItem;
        this.itemObject = itemObject;
        this.idSaleReceiptReceiptReturnDetail = idSaleReceiptReceiptReturnDetail;
        this.quantityReceiptDetail = quantityReceiptDetail;
        this.priceReceiptDetail = priceReceiptDetail;
        this.sumReceiptDetail = sumReceiptDetail;
        this.discountPercentReceiptDetail = discountPercentReceiptDetail;
        this.discountSumReceiptDetail = discountSumReceiptDetail;
        this.discountSumReceipt = discountSumReceipt;
        this.seriesNumberDiscountCard = seriesNumberDiscountCard;
        this.numberReceiptDetail = numberReceiptDetail;
        this.filename = filename;
        this.idSection = idSection;
        this.skipReceipt = skipReceipt;
        this.cashRegisterInfo = cashRegisterInfo;
    }

    private void setSumGiftCardMap(BigDecimal sumGiftCard) {
        Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
        sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
        this.sumGiftCardMap = sumGiftCardMap;
    }

    //startDate - для обратной совместимости
    public String getIdZReport() {
        return nppGroupMachinery + "_" + nppMachinery + "_" + numberZReport + (dateZReport != null ? ("_" + new SimpleDateFormat("ddMMyyyy").format(dateZReport)) : "");
    }
    
    public String getIdReceipt(Boolean timeId) {
        return getIdZReport() + "_" + numberReceipt + (timeId != null && timeId.equals(Boolean.TRUE) ? "_" + timeReceipt : "");
    }

    public String getIdReceiptDetail(Boolean timeId) {
        return getIdReceipt(timeId) + "_" + numberReceiptDetail;
    }
}
