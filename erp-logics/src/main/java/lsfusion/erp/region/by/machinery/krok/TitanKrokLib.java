package lsfusion.erp.region.by.machinery.krok;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.io.File;
import java.nio.charset.StandardCharsets;

// Обёртка над библиотекой платежей "Крок" TitanKrokLib (TitanKrokLib.dll / libtitankroklib.so), функции экспортируются как extern "C"
public class TitanKrokLib {

    private static final int OUT_MAX_SIZE = 65536;
    private static final int ERROR_MAX_SIZE = 4096;

    public interface TextCallback extends Callback {
        void invoke(Pointer data, int size);
    }

    public interface TitanKrokDLL extends Library {
        // bool execute(const char* dataIn, int dataInSize, char* dataOut, int outMaxSize);
        // C++ bool занимает один байт, поэтому возвращаемое значение читаем как byte
        byte execute(byte[] dataIn, int dataInSize, byte[] dataOut, int outMaxSize);

        void getLastError(byte[] buffer, int maxSize);

        void registerShowQrCallback(TextCallback cb);

        void registerShowProgressText(TextCallback cb);

        void interrupt();

        void setCncp(int cncp);
    }

    // Qt внутри библиотеки ищет плагины (tls/ и др.) относительно исполняемого файла (java), поэтому указываем каталог библиотеки явно.
    // В Windows Qt собран под msvcrt, у которого своя копия окружения, поэтому меняем и её, и окружение процесса
    private interface Kernel32Env extends Library {
        boolean SetEnvironmentVariableW(WString name, WString value);
    }

    private interface MsvcrtEnv extends Library {
        int _wputenv_s(WString name, WString value);
    }

    private interface LibcEnv extends Library {
        int setenv(String name, String value, int overwrite);
    }

    public interface Listener {
        void showQr(String qr);

        void showProgress(String text);
    }

    private static TitanKrokDLL lib;
    private static String libPath;
    private static volatile Listener listener;

    // библиотека хранит указатели на callback'и, поэтому держим на них сильные ссылки
    private static final TextCallback qrCallback = new TextCallback() {
        public void invoke(Pointer data, int size) {
            Listener l = listener;
            if (l != null)
                l.showQr(TitanKrokLib.toString(data, size));
        }
    };
    private static final TextCallback progressCallback = new TextCallback() {
        public void invoke(Pointer data, int size) {
            Listener l = listener;
            if (l != null)
                l.showProgress(TitanKrokLib.toString(data, size));
        }
    };

    // libraryDir - каталог, в который распакована библиотека (рядом с ней лежит TitanKrokLib.conf); если не задан - ищется стандартным образом
    public static synchronized TitanKrokDLL getLib(String libraryDir) {
        String path = getLibPath(libraryDir);
        if (lib == null || !path.equals(libPath)) {
            boolean hasDir = libraryDir != null && !libraryDir.trim().isEmpty();
            if (hasDir && !new File(path).isFile())
                throw new RuntimeException("Не найден файл " + path);
            if (hasDir)
                setQtPluginPath(new File(libraryDir.trim()).getAbsolutePath());
            try {
                lib = Native.load(path, TitanKrokDLL.class);
            } catch (UnsatisfiedLinkError e) {
                // JNA дописывает в сообщение поиск в ресурсах со всем classpath, его отрезаем
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                int resourceInfo = message.indexOf("Native library");
                if (resourceInfo > 0)
                    message = message.substring(0, resourceInfo);
                message = message.trim().replaceAll("\\s+", " ");
                throw new RuntimeException(message + (hasDir ? ". Проверьте, что в каталоге " + libraryDir.trim() +
                        " есть все файлы из архива библиотеки (Qt6Core, Qt6Network, libssl, libcrypto и др.)" : ""));
            }
            libPath = path;
            lib.registerShowQrCallback(qrCallback);
            lib.registerShowProgressText(progressCallback);
        }
        return lib;
    }

    private static void setQtPluginPath(String dir) {
        // плагины могут лежать как прямо в каталоге библиотеки (tls/), так и в его подкаталоге plugins/
        String value = dir + File.pathSeparator + new File(dir, "plugins").getPath();
        try {
            if (Platform.isWindows()) {
                Native.load("kernel32", Kernel32Env.class).SetEnvironmentVariableW(new WString("QT_PLUGIN_PATH"), new WString(value));
                Native.load("msvcrt", MsvcrtEnv.class)._wputenv_s(new WString("QT_PLUGIN_PATH"), new WString(value));
            } else
                Native.load("c", LibcEnv.class).setenv("QT_PLUGIN_PATH", value, 1);
        } catch (Throwable ignored) {
        }
    }

    // Windows: TitanKrokLib.dll; Linux: libTitanKrokLib.so (в документации libtitankroklib.so - проверяем оба имени)
    private static String getLibPath(String libraryDir) {
        if (libraryDir == null || libraryDir.trim().isEmpty())
            return "TitanKrokLib";
        File file = new File(libraryDir.trim(), System.mapLibraryName("TitanKrokLib"));
        if (!Platform.isWindows() && !file.isFile()) {
            File lowerCase = new File(libraryDir.trim(), System.mapLibraryName("titankroklib"));
            if (lowerCase.isFile())
                file = lowerCase;
        }
        return file.getAbsolutePath();
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    // возвращает json-ответ библиотеки, при ошибке бросает исключение с текстом getLastError
    public static String execute(TitanKrokDLL lib, String request) {
        byte[] dataIn = request.getBytes(StandardCharsets.UTF_8);
        byte[] dataOut = new byte[OUT_MAX_SIZE];
        if (lib.execute(dataIn, dataIn.length, dataOut, OUT_MAX_SIZE) == 0)
            throw new RuntimeException(getLastError(lib));
        return Native.toString(dataOut, StandardCharsets.UTF_8.name());
    }

    public static String getLastError(TitanKrokDLL lib) {
        byte[] buffer = new byte[ERROR_MAX_SIZE];
        lib.getLastError(buffer, ERROR_MAX_SIZE);
        String error = Native.toString(buffer, StandardCharsets.UTF_8.name());
        return error.isEmpty() ? "Неизвестная ошибка библиотеки TitanKrokLib" : error;
    }

    private static String toString(Pointer data, int size) {
        if (data == null || size <= 0)
            return "";
        byte[] bytes = data.getByteArray(0, size);
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0)
            length--;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
