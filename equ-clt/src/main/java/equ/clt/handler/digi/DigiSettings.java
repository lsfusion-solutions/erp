package equ.clt.handler.digi;

import java.io.Serializable;

public class DigiSettings implements Serializable{

    private Integer maxLineLength;

    public DigiSettings() {
    }

    public Integer getMaxLineLength() {
        return maxLineLength;
    }

    public void setMaxLineLength(Integer maxLineLength) {
        this.maxLineLength = maxLineLength;
    }
}