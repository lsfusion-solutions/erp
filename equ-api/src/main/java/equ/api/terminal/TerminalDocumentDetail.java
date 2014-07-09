package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;

public class TerminalDocumentDetail implements Serializable {
    public String numberTerminalDocument;
    public String directoryGroupTerminal;
    public String idTerminalHandbookType1;
    public String idTerminalHandbookType2;
    public String idTerminalDocumentType;
    public String idTerminalDocumentDetail;
    public String numberTerminalDocumentDetail;
    public String barcodeTerminalDocumentDetail;
    public BigDecimal priceTerminalDocumentDetail;
    public BigDecimal quantityTerminalDocumentDetail;
    public BigDecimal sumTerminalDocumentDetail;
    
    public TerminalDocumentDetail(String numberTerminalDocument, String directoryGroupTerminal, String idTerminalHandbookType1,
                                  String idTerminalHandbookType2, String idTerminalDocumentType, String idTerminalDocumentDetail,
                                  String numberTerminalDocumentDetail, String barcodeTerminalDocumentDetail, 
                                  BigDecimal priceTerminalDocumentDetail, BigDecimal quantityTerminalDocumentDetail, 
                                  BigDecimal sumTerminalDocumentDetail) {
        this.numberTerminalDocument = numberTerminalDocument;
        this.directoryGroupTerminal = directoryGroupTerminal;
        this.idTerminalHandbookType1 = idTerminalHandbookType1;
        this.idTerminalHandbookType2 = idTerminalHandbookType2;
        this.idTerminalDocumentType = idTerminalDocumentType;
        this.idTerminalDocumentDetail = idTerminalDocumentDetail;
        this.numberTerminalDocumentDetail = numberTerminalDocumentDetail;
        this.barcodeTerminalDocumentDetail = barcodeTerminalDocumentDetail;
        this.priceTerminalDocumentDetail = priceTerminalDocumentDetail;
        this.quantityTerminalDocumentDetail = quantityTerminalDocumentDetail;
        this.sumTerminalDocumentDetail = sumTerminalDocumentDetail;
    }
}
