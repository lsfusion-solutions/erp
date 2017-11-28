package equ.clt.handler.digi;

import java.io.Serializable;

public class DigiSM120Settings implements Serializable{

    private Integer nameLineFont;
    private Integer nameLineLength;

    public DigiSM120Settings() {
    }

    public void setNameLineFont(Integer nameLineFont) {
        this.nameLineFont = nameLineFont;
    }

    public Integer getNameLineFont() {
        return nameLineFont;
    }

    public void setNameLineLength(Integer nameLineLength) {
        this.nameLineLength = nameLineLength;
    }

    public Integer getNameLineLength() {
        return nameLineLength;
    }
}