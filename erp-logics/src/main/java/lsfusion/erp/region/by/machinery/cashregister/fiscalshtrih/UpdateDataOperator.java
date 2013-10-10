package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import java.io.Serializable;

public class UpdateDataOperator implements Serializable {
    public Integer operatorPassword;
    public String operatorName;

    public UpdateDataOperator(Integer operatorPassword, String operatorName) {
        this.operatorPassword = operatorPassword;
        this.operatorName = operatorName;
    }
}
