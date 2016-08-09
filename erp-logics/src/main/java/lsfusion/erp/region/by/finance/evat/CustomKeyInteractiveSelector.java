package lsfusion.erp.region.by.finance.evat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import by.avest.edoc.client.PersonalKeyManager;

//Класс для выбора контейнера личного ключа из запроса пароля к ключу
public class CustomKeyInteractiveSelector extends PersonalKeyManager {
	String password;

	public CustomKeyInteractiveSelector(KeyStore ks) {
		super(ks);
	}

	public CustomKeyInteractiveSelector(String password) throws UnrecoverableKeyException,
			KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException {
		super(getDefaultKS());
		this.password = password;
	}

	private static KeyStore getDefaultKS() throws KeyStoreException,
			NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore ks = KeyStore.getInstance("AvPersonal");
		ks.load(null, null);
		return ks;
	}

	@Override
	public char[] promptPassword(String alias) throws IOException {
		// command line interface could be replaced with GUI
		//String request = "Введите пароль для ключа \"" + alias + "\": ";
		return password.toCharArray();//promptPasswordInternal(request);
	}

	private char[] promptPasswordInternal(String request) throws IOException {
		char[] answer = promptForPassword(request);
		// validate entered password
		if ((answer != null) && (answer.length >= 8)) {
			return answer;
		} else {
			return promptPasswordInternal("Минимальная длина пароля 8 символов, повторите ввод пароля: ");
		}
	}

	private char[] promptForPassword(String request) throws IOException {
		System.out.print(request);
		// read password frm standard input
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(System.in));
		String line = bufferedReader.readLine();
		return line == null ? null : line.toCharArray();
	}

	@Override
	public String chooseAlias(String[] aliases) throws IOException {
		System.out.println("Список ключей:");
		for (int i = 0; i < aliases.length; i++) {
			System.out.println((i + 1) + ": " + aliases[i]);
		}

		return aliases[promptAliasIndex(aliases)];
	}

	private int promptAliasIndex(String[] aliases) throws IOException {
		System.out.print("Введите номер ключа: ");
		return Integer.parseInt(promptAliasIndexInternal(aliases)) - 1;
	}

	private String promptAliasIndexInternal(String[] aliases)
			throws IOException {
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(System.in));
		String line = bufferedReader.readLine();
		if (isAliasValid(line, aliases)) {
			return line;
		} else {
			System.out.print("Введен неверный номер ключа, повторите выбор: ");
			return promptAliasIndexInternal(aliases);
		}
	}

	private boolean isAliasValid(String line, String[] aliases) {
		try {
			int index = Integer.parseInt(line);
			return (index > 0) && (index <= aliases.length);
		} catch (NumberFormatException e) {
			return false;
		}
	}

}