package equ.api.terminal;

import equ.api.TransactionInfo;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionTerminalInfo extends TransactionInfo<TerminalInfo, TerminalItemInfo> {
    public List<TerminalHandbookType> terminalHandbookTypeList;
    public List<TerminalDocumentType> terminalDocumentTypeList;
    public List<TerminalLegalEntity> terminalLegalEntityList;
    public List<TerminalAssortment> terminalAssortmentList;
    public Integer nppGroupTerminal;
    public String directoryGroupTerminal;
    
    public TransactionTerminalInfo(Long id, String dateTimeCode, Date date, String handlerModel, Long idGroupMachinery, Integer nppGroupMachinery, 
                                   String nameGroupMachinery, String description, List<TerminalItemInfo> itemsList, 
                                   List<TerminalInfo> machineryInfoList, Boolean snapshot, Timestamp lastErrorDate,
                                   List<TerminalHandbookType> terminalHandbookTypeList, List<TerminalDocumentType> terminalDocumentTypeList,
                                   List<TerminalLegalEntity> terminalLegalEntityList, List<TerminalAssortment> terminalAssortmentList,
                                   Integer nppGroupTerminal, String directoryGroupTerminal, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description,
                null, itemsList, machineryInfoList, snapshot, lastErrorDate, info);
        this.terminalHandbookTypeList = terminalHandbookTypeList;
        this.terminalDocumentTypeList = terminalDocumentTypeList;
        this.terminalLegalEntityList = terminalLegalEntityList;
        this.terminalAssortmentList = terminalAssortmentList;
        this.nppGroupTerminal = nppGroupTerminal;
        this.directoryGroupTerminal = directoryGroupTerminal;
    }
}
