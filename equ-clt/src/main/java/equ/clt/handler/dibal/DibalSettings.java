package equ.clt.handler.dibal;

import java.io.Serializable;

public class DibalSettings implements Serializable {
    //barcode format (EAN-13 format number on the scales, H3 register, 2 digits) for weight items
    private String weightEan13Format = "01";

    //barcode format (EAN-13 format number on the scales, H3 register, 2 digits) for piece items
    private String pieceEan13Format = "02";

    public DibalSettings() {}

    public String getWeightEan13Format() {
        return weightEan13Format;
    }

    public void setWeightEan13Format(String weightEan13Format) {
        this.weightEan13Format = weightEan13Format;
    }

    public String getPieceEan13Format() {
        return pieceEan13Format;
    }

    public void setPieceEan13Format(String pieceEan13Format) {
        this.pieceEan13Format = pieceEan13Format;
    }
}
