package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;

public class SalesInfo implements Serializable {
    public String numberCashRegister;
    public String numberZReport;
    public Integer numberReceipt;
    public Date dateReceipt;
    public Time timeReceipt;
    public BigDecimal sumReceipt;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public String barcodeItem;
    public BigDecimal quantityReceiptDetail;
    public BigDecimal priceReceiptDetail;
    public BigDecimal sumReceiptDetail;
    public BigDecimal discountSumReceiptDetail;
    public BigDecimal discountSumReceipt;
    public String seriesNumberDiscountCard;
    public Integer numberReceiptDetail;
    public String filename;

    public SalesInfo(String numberCashRegister, String numberZReport, Integer numberReceipt, Date dateReceipt, Time timeReceipt,
                     BigDecimal sumReceipt, BigDecimal sumCard, BigDecimal sumCash, String barcodeItem, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountSumReceiptDetail, BigDecimal discountSumReceipt,
                     String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename) {
        this.numberCashRegister = numberCashRegister;
        this.numberZReport = numberZReport;
        this.numberReceipt = numberReceipt;
        this.dateReceipt = dateReceipt;
        this.timeReceipt = timeReceipt;
        this.sumReceipt = sumReceipt;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.barcodeItem = barcodeItem;
        this.quantityReceiptDetail = quantityReceiptDetail;
        this.priceReceiptDetail = priceReceiptDetail;
        this.sumReceiptDetail = sumReceiptDetail;
        this.discountSumReceiptDetail = discountSumReceiptDetail;
        this.discountSumReceipt = discountSumReceipt;
        this.seriesNumberDiscountCard = seriesNumberDiscountCard;
        this.numberReceiptDetail = numberReceiptDetail;
        this.filename = filename;
    }
}
