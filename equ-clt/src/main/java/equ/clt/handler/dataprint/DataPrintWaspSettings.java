package equ.clt.handler.dataprint;

import java.io.Serializable;

public class DataPrintWaspSettings implements Serializable{
    
    private Integer descriptionLineLength;
    private Integer labelBill1;
    private Integer barcodeBill1;
    private Integer labelBill2;
    private Integer barcodeBill2;
    
    public DataPrintWaspSettings() {
    }
    
    public Integer getDescriptionLineLength() {
        return descriptionLineLength;
    }
    
    public void setDescriptionLineLength(Integer descriptionLineLength) {
        this.descriptionLineLength = descriptionLineLength;
    }
    
    public void setLabelBill1(Integer labelBill1) {
        this.labelBill1 = labelBill1;
    }
    
    public Integer getLabelBill1() {
        return labelBill1;
    }
    
    public void setBarcodeBill1(Integer barcodeBill1) {
        this.barcodeBill1 = barcodeBill1;
    }
    
    public Integer getBarcodeBill1() {
        return barcodeBill1;
    }
    
    public Integer getLabelBill2() {
        return labelBill2;
    }
    
    public void setLabelBill2(Integer labelBill2) {
        this.labelBill2 = labelBill2;
    }
    
    public Integer getBarcodeBill2() {
        return barcodeBill2;
    }
    
    public void setBarcodeBill2(Integer barcodeBill2) {
        this.barcodeBill2 = barcodeBill2;
    }
}