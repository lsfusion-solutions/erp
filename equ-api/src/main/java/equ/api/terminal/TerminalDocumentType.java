package equ.api.terminal;

import java.io.Serializable;

public class TerminalDocumentType implements Serializable {
    public String id;
    public String name;
    
    public TerminalDocumentType(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
