package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }
}