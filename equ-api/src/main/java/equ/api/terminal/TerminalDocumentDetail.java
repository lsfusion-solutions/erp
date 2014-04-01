package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;

public class TerminalDocumentDetail implements Serializable {
    public String numberTerminalDocument;
    public String idTerminalHandbookType1;
    public String idTerminalHandbookType2;
    public String idTerminalDocumentType;
    public String nameTerminalDocumentDetail;
    public String barcodeTerminalDocumentDetail;
    public BigDecimal priceTerminalDocumentDetail;
    public BigDecimal quantityTerminalDocumentDetail;
    
    public TerminalDocumentDetail(String numberTerminalDocument, String idTerminalHandbookType1, String idTerminalHandbookType2, 
                                  String idTerminalDocumentType, String nameTerminalDocumentDetail, String barcodeTerminalDocumentDetail, 
                                  BigDecimal priceTerminalDocumentDetail, BigDecimal quantityTerminalDocumentDetail) {
        this.numberTerminalDocument = numberTerminalDocument;
        this.idTerminalHandbookType1 = idTerminalHandbookType1;
        this.idTerminalHandbookType2 = idTerminalHandbookType2;
        this.idTerminalDocumentType = idTerminalDocumentType;
        this.nameTerminalDocumentDetail = nameTerminalDocumentDetail;
        this.barcodeTerminalDocumentDetail = barcodeTerminalDocumentDetail;
        this.priceTerminalDocumentDetail = priceTerminalDocumentDetail;
        this.quantityTerminalDocumentDetail = quantityTerminalDocumentDetail;
    }
}
