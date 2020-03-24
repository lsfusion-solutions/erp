package lsfusion.erp.region.by.certificate.declaration;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class Declaration {
    
    public String number;
    public LocalDate date;
    public String UNPLegalEntity;
    public String fullNameLegalEntity;
    public String addressLegalEntity;
    public List<DeclarationDetail> declarationDetailList;
    public BigDecimal sum;
    public Integer count;
    
    public Declaration(String number, LocalDate date, String UNPLegalEntity, String fullNameLegalEntity,
                       String addressLegalEntity, List<DeclarationDetail> declarationDetailList, BigDecimal sum,
                       Integer count) {
        this.number = number;
        this.date = date;
        this.UNPLegalEntity = UNPLegalEntity;
        this.fullNameLegalEntity = fullNameLegalEntity;
        this.addressLegalEntity = addressLegalEntity;
        this.declarationDetailList = declarationDetailList;
        this.sum = sum;
        this.count = count;
    }
}
