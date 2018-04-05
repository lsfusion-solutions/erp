package equ.clt.handler.astron;

import java.io.Serializable;

public class AstronSettings implements Serializable {
    private Integer timeout;

    public AstronSettings() {
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}