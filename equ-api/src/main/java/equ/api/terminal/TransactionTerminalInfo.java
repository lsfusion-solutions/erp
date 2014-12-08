package equ.api.terminal;

import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.List;

public class TransactionTerminalInfo extends TransactionInfo<TerminalInfo, TerminalItemInfo> {
    public List<TerminalHandbookType> terminalHandbookTypeList;
    public List<TerminalDocumentType> terminalDocumentTypeList;
    public List<TerminalLegalEntity> terminalLegalEntityList;
    public List<TerminalAssortment> terminalAssortmentList;
    public Integer nppGroupTerminal;
    public String directoryGroupTerminal;
    
    public TransactionTerminalInfo(Integer id, String dateTimeCode, String handlerModel, Integer idGroupMachinery, Integer nppGroupMachinery, 
                                   String nameGroupMachinery, String comment, List<TerminalItemInfo> itemsList, 
                                   List<TerminalInfo> machineryInfoList, Boolean snapshot, List<TerminalHandbookType> terminalHandbookTypeList, 
                                   List<TerminalDocumentType> terminalDocumentTypeList, List<TerminalLegalEntity> terminalLegalEntityList, 
                                   List<TerminalAssortment> terminalAssortmentList, Integer nppGroupTerminal, String directoryGroupTerminal) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.handlerModel = handlerModel;
        this.idGroupMachinery = idGroupMachinery;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nameGroupMachinery = nameGroupMachinery;
        this.comment = comment;
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

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<TerminalInfo> machineryInfoList) throws IOException {
        return ((TerminalHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
