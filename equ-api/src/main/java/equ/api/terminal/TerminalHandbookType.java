package equ.api.terminal;

import java.io.Serializable;

public class TerminalHandbookType implements Serializable {
    public String id;
    public String name;
    
    public TerminalHandbookType(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
