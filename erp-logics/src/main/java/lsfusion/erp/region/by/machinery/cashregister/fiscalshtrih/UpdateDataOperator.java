package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import java.io.Serializable;

public class UpdateDataOperator implements Serializable {
    public Integer operatorPassword;
    public Integer operatorNumber;
    public String operatorName;

    public UpdateDataOperator(Integer operatorPassword, Integer operatorNumber, String operatorName) {
        this.operatorPassword = operatorPassword;
        this.operatorNumber = operatorNumber;
        this.operatorName = operatorName;
    }
}
