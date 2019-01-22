package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;

public class ZReportInfo implements Serializable {
    public BigDecimal externalSum;
    public BigDecimal sumProtectedEnd;
    public BigDecimal sumBack;

    public ZReportInfo(BigDecimal externalSum, BigDecimal sumProtectedEnd, BigDecimal sumBack) {
        this.externalSum = externalSum;
        this.sumProtectedEnd = sumProtectedEnd;
        this.sumBack = sumBack;
    }
}