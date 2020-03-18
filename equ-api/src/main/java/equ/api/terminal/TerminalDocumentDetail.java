package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class TerminalDocumentDetail implements Serializable {
    public String idDocument;
    public String numberDocument;
    public LocalDate dateDocument;
    public LocalTime timeDocument;
    public String commentDocument;
    public String directoryGroupTerminal;
    public String idTerminalHandbookType1;
    public String idTerminalHandbookType2;
    public String idTerminalDocumentType;
    public BigDecimal quantityDocument;
    public String idDocumentDetail;
    public String numberDocumentDetail;
    public String barcodeDocumentDetail;
    public String nameDocumentDetail;
    public BigDecimal priceDocumentDetail;
    public BigDecimal quantityDocumentDetail;
    public BigDecimal sumDocumentDetail;
    
    public TerminalDocumentDetail(String idDocument, String numberDocument, LocalDate dateDocument, LocalTime timeDocument, String commentDocument,
                                  String directoryGroupTerminal, String idTerminalHandbookType1, String idTerminalHandbookType2,
                                  String idTerminalDocumentType, BigDecimal quantityDocument, String idDocumentDetail,
                                  String numberDocumentDetail,  String barcodeDocumentDetail, String nameDocumentDetail,
                                  BigDecimal priceDocumentDetail, BigDecimal quantityDocumentDetail, BigDecimal sumDocumentDetail) {
        this.idDocument = idDocument;
        this.numberDocument = numberDocument;
        this.dateDocument = dateDocument;
        this.timeDocument = timeDocument;
        this.commentDocument = commentDocument;
        this.directoryGroupTerminal = directoryGroupTerminal;
        this.idTerminalHandbookType1 = idTerminalHandbookType1;
        this.idTerminalHandbookType2 = idTerminalHandbookType2;
        this.idTerminalDocumentType = idTerminalDocumentType;
        this.quantityDocument = quantityDocument;
        this.idDocumentDetail = idDocumentDetail;
        this.numberDocumentDetail = numberDocumentDetail;
        this.barcodeDocumentDetail = barcodeDocumentDetail;
        this.nameDocumentDetail = nameDocumentDetail;
        this.priceDocumentDetail = priceDocumentDetail;
        this.quantityDocumentDetail = quantityDocumentDetail;
        this.sumDocumentDetail = sumDocumentDetail;
    }
}
