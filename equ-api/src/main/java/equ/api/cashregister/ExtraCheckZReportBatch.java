package equ.api.cashregister;

import java.util.Set;

public class ExtraCheckZReportBatch {
    public Set<String> idZReportSet;
    public String message;

    public ExtraCheckZReportBatch(Set<String> idZReportSet, String message) {
        this.idZReportSet = idZReportSet;
        this.message = message;
    }
}
