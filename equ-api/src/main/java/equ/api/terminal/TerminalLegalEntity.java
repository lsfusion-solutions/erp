package equ.api.terminal;

import java.io.Serializable;

public class TerminalLegalEntity implements Serializable {
    public String idLegalEntity;
    public String nameLegalEntity;
    public String extInfo;
    
    public TerminalLegalEntity(String idLegalEntity, String nameLegalEntity, String extInfo) {
        this.idLegalEntity = idLegalEntity;
        this.nameLegalEntity = nameLegalEntity;
        this.extInfo = extInfo;
    }
}
