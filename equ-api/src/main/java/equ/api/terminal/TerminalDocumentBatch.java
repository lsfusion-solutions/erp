package equ.api.terminal;

import java.util.List;

public class TerminalDocumentBatch  {
    public List<TerminalDocumentDetail> documentDetailList;
    public List<String> readFiles;

    public TerminalDocumentBatch(List<TerminalDocumentDetail> documentDetailList, List<String> readFiles) {
        this.documentDetailList = documentDetailList;
        this.readFiles = readFiles;
    }
}
