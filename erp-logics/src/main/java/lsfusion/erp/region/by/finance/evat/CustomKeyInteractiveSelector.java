//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package lsfusion.erp.region.by.finance.evat;

import by.avest.edoc.client.PersonalKeyManager;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class CustomKeyInteractiveSelector extends PersonalKeyManager {
    int certIndex;

    public CustomKeyInteractiveSelector(KeyStore ks) {
        super(ks);
    }

    public CustomKeyInteractiveSelector(int certIndex) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        super(getDefaultKS());
        this.certIndex = certIndex;
    }

    private static KeyStore getDefaultKS() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore ks = KeyStore.getInstance("AvPersonal");
        ks.load(null, null);
        return ks;
    }

    public char[] promptPassword(String alias) {
        String request = "Введите пароль для ключа \"" + alias + "\": ";
        return this.promptPasswordInternal(request);
    }

    private char[] promptPasswordInternal(String request) {
        Console console = System.console();
        console.printf(request, new Object[0]);
        char[] answer = console.readPassword();
        return answer != null && answer.length >= 8?answer:this.promptPasswordInternal("Минимальная длина пароля 8 символов, повторите ввод пароля: ");
    }

    public String chooseAlias(String[] aliases) {
        /*System.out.println("Список ключей:");

        for(int i = 0; i < aliases.length; ++i) {
            System.out.println(i + 1 + ": " + aliases[i]);
        }

        return aliases[this.promptAliasIndex(aliases)];*/
        return aliases[Math.min(certIndex, aliases.length - 1)];
    }

    /*private int promptAliasIndex(String[] aliases) throws IOException {
        System.out.print("Введите номер ключа: ");
        return Integer.parseInt(this.promptAliasIndexInternal(aliases)) - 1;
    }*/

    private String promptAliasIndexInternal(String[] aliases) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String line = bufferedReader.readLine();
        if(this.isAliasValid(line, aliases)) {
            return line;
        } else {
            System.out.print("Введен неверный номер ключа, повторите выбор: ");
            return this.promptAliasIndexInternal(aliases);
        }
    }

    private boolean isAliasValid(String line, String[] aliases) {
        try {
            int e = Integer.parseInt(line);
            return e > 0 && e <= aliases.length;
        } catch (NumberFormatException var4) {
            return false;
        }
    }
}
