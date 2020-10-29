package equ.clt.handler.digi;

import java.io.Serializable;

public class DigiSettings implements Serializable{

    //максимальная длина строки в составе. По умолчанию 50
    private Integer maxLineLength;

    //максимальная длина строки наименования. Если не задано, то по строкам не разбивается
    private Integer maxNameLength;

    //максимальное кол-во строк наименования. Если не задано, то не ограничено
    private Integer maxNameLinesCount;

    //размер шрифта. По умолчанию 4
    private Integer fontSize;

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

    public Integer getMaxNameLinesCount() {
        return maxNameLinesCount;
    }

    public void setMaxNameLinesCount(Integer maxNameLinesCount) {
        this.maxNameLinesCount = maxNameLinesCount;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }
}