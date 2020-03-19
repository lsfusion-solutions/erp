package lsfusion.erp.region.by.certificate.declaration;


import java.time.LocalDate;

public class G44Detail {

    public Integer numberDeclarationDetail;
    public Long order;
    public String KD;
    public String ND;
    public LocalDate DD;
    public LocalDate beginDate;
    public LocalDate endDate;
    public String country;
    public String vidplat;
    public String refdoc;
    public String description;
    public String numberDeclaration;
    public LocalDate dateDeclaration;

    public G44Detail(Integer numberDeclarationDetail, Long order, String KD, String ND, LocalDate DD, LocalDate beginDate, LocalDate endDate,
                     String country, String vidplat, String refdoc, String description, String numberDeclaration, LocalDate dateDeclaration) {
        this.numberDeclarationDetail = numberDeclarationDetail;
        this.order = order;
        this.KD = KD;
        this.ND = ND;
        this.DD = DD;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.country = country;
        this.vidplat = vidplat;
        this.refdoc = refdoc;
        this.description = description;
        this.numberDeclaration = numberDeclaration;
        this.dateDeclaration = dateDeclaration;
    }
}
