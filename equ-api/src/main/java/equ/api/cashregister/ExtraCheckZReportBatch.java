package equ.api.cashregister;

import java.util.List;

public class ExtraCheckZReportBatch {
    public List<String> idZReportList;
    public String message;

    public ExtraCheckZReportBatch(List<String> idZReportList, String message) {
        this.idZReportList = idZReportList;
        this.message = message;
    }
}
