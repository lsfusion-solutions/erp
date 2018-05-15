package equ.clt.handler.digi;

import java.io.Serializable;

public class DigiSettings implements Serializable{

    private Integer maxLineLength;
    private Integer maxNameLength;

    public DigiSettings() {
    }

    public Integer getMaxLineLength() {
        return maxLineLength;
    }

    public void setMaxLineLength(Integer maxLineLength) {
        this.maxLineLength = maxLineLength;
    }

    public Integer getMaxNameLength() {
        return maxNameLength;
    }

    public void setMaxNameLength(Integer maxNameLength) {
        this.maxNameLength = maxNameLength;
    }
}