package equ.clt.handler.digi;

import java.io.Serializable;

public class DigiSM120Settings implements Serializable{

    private Integer nameLineFont;
    private Integer nameLineLength;
    private Integer descriptionLineFont;
    private Integer descriptionLineLength;

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

    public Integer getDescriptionLineFont() {
        return descriptionLineFont;
    }

    public void setDescriptionLineFont(Integer descriptionLineFont) {
        this.descriptionLineFont = descriptionLineFont;
    }

    public Integer getDescriptionLineLength() {
        return descriptionLineLength;
    }

    public void setDescriptionLineLength(Integer descriptionLineLength) {
        this.descriptionLineLength = descriptionLineLength;
    }
}