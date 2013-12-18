package lsfusion.erp.region.by.certificate.declaration;


import java.util.List;

public class G44 {
    
    public String number;
    public List<G44Detail> g44DetailList;
    
    public G44(String number, List<G44Detail> g44DetailList) {
        this.number = number;        
        this.g44DetailList = g44DetailList;            
    }
}
