package equ.api.terminal;

import java.io.Serializable;

public class TerminalLegalEntity implements Serializable {
    public String idLegalEntity;
    public String nameLegalEntity;
    
    public TerminalLegalEntity(String idLegalEntity, String nameLegalEntity) {
        this.idLegalEntity = idLegalEntity;
        this.nameLegalEntity = nameLegalEntity;
    }
}
