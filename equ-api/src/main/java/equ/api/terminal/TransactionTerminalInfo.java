package equ.api.terminal;

import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.sql.Date;
import java.util.List;

public class TransactionTerminalInfo extends TransactionInfo<TerminalInfo, TerminalItemInfo> {
    public List<TerminalHandbookType> terminalHandbookTypeList;
    public List<TerminalDocumentType> terminalDocumentTypeList;
    public List<TerminalLegalEntity> terminalLegalEntityList;
    public List<TerminalAssortment> terminalAssortmentList;
    public Integer nppGroupTerminal;
    public String directoryGroupTerminal;
    
    public TransactionTerminalInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery, Integer nppGroupMachinery, 
                                   String nameGroupMachinery, String description, List<TerminalItemInfo> itemsList, 
                                   List<TerminalInfo> machineryInfoList, Boolean snapshot, List<TerminalHandbookType> terminalHandbookTypeList, 
                                   List<TerminalDocumentType> terminalDocumentTypeList, List<TerminalLegalEntity> terminalLegalEntityList, 
                                   List<TerminalAssortment> terminalAssortmentList, Integer nppGroupTerminal, String directoryGroupTerminal) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.handlerModel = handlerModel;
        this.idGroupMachinery = idGroupMachinery;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nameGroupMachinery = nameGroupMachinery;
        this.description = description;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
        this.terminalHandbookTypeList = terminalHandbookTypeList;
        this.terminalDocumentTypeList = terminalDocumentTypeList;
        this.terminalLegalEntityList = terminalLegalEntityList;
        this.terminalAssortmentList = terminalAssortmentList;
        this.nppGroupTerminal = nppGroupTerminal;
        this.directoryGroupTerminal = directoryGroupTerminal;
    }
}
