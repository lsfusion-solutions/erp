package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;

public class TerminalDocumentDetail implements Serializable {
    public String idTerminalDocument;
    public String numberTerminalDocument;
    public String directoryGroupTerminal;
    public String idTerminalHandbookType1;
    public String idTerminalHandbookType2;
    public String idTerminalDocumentType;
    public BigDecimal quantityTerminalDocument;
    public String idTerminalDocumentDetail;
    public String numberTerminalDocumentDetail;
    public String barcodeTerminalDocumentDetail;
    public String nameTerminalDocumentDetail;
    public BigDecimal priceTerminalDocumentDetail;
    public BigDecimal quantityTerminalDocumentDetail;
    public BigDecimal sumTerminalDocumentDetail;
    
    public TerminalDocumentDetail(String idTerminalDocument, String numberTerminalDocument, String directoryGroupTerminal,
                                  String idTerminalHandbookType1, String idTerminalHandbookType2, String idTerminalDocumentType,
                                  BigDecimal quantityTerminalDocument, String idTerminalDocumentDetail, String numberTerminalDocumentDetail, 
                                  String barcodeTerminalDocumentDetail, String nameTerminalDocumentDetail, BigDecimal priceTerminalDocumentDetail, 
                                  BigDecimal quantityTerminalDocumentDetail, BigDecimal sumTerminalDocumentDetail) {
        this.idTerminalDocument = idTerminalDocument;
        this.numberTerminalDocument = numberTerminalDocument;
        this.directoryGroupTerminal = directoryGroupTerminal;
        this.idTerminalHandbookType1 = idTerminalHandbookType1;
        this.idTerminalHandbookType2 = idTerminalHandbookType2;
        this.idTerminalDocumentType = idTerminalDocumentType;
        this.quantityTerminalDocument = quantityTerminalDocument;
        this.idTerminalDocumentDetail = idTerminalDocumentDetail;
        this.numberTerminalDocumentDetail = numberTerminalDocumentDetail;
        this.barcodeTerminalDocumentDetail = barcodeTerminalDocumentDetail;
        this.nameTerminalDocumentDetail = nameTerminalDocumentDetail;
        this.priceTerminalDocumentDetail = priceTerminalDocumentDetail;
        this.quantityTerminalDocumentDetail = quantityTerminalDocumentDetail;
        this.sumTerminalDocumentDetail = sumTerminalDocumentDetail;
    }
}
