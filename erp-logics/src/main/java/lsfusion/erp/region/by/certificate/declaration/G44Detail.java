package lsfusion.erp.region.by.certificate.declaration;


import java.util.Date;

public class G44Detail {

    public Integer numberDeclarationDetail;
    public Long order;
    public String KD;
    public String ND;
    public Date DD;
    public Date beginDate;
    public Date endDate;
    public String country;
    public String vidplat;
    public String refdoc;
    public String description;
    public String numberDeclaration;
    public Date dateDeclaration;

    public G44Detail(Integer numberDeclarationDetail, Long order, String KD, String ND, Date DD, Date beginDate, Date endDate,
                     String country, String vidplat, String refdoc, String description, String numberDeclaration, Date dateDeclaration) {
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
