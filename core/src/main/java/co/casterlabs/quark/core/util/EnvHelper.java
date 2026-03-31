package co.casterlabs.quark.core.util;

public class EnvHelper {

    public static String string(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public static int integer(String key, int defaultValue) {
        String value = System.getenv(key);

        if (value != null) {
            return Integer.parseInt(value);
        }

        return defaultValue;
    }

    public static boolean bool(String key, boolean defaultValue) {
        String value = System.getenv(key);

        if (value != null) {
            return "true".equalsIgnoreCase(value);
        }

        return defaultValue;
    }

}
