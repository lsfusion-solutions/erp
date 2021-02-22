package equ.api.terminal;

import java.io.Serializable;

public class TerminalDocumentType implements Serializable {
    public String id;
    public String name;
    public String analytics1;
    public String analytics2;
    public Long flag;
    
    public TerminalDocumentType(String id, String name, String analytics1, String analytics2, Long flag) {
        this.id = id;
        this.name = name;
        this.analytics1 = analytics1;
        this.analytics2 = analytics2;
        this.flag = flag;
    }
}
