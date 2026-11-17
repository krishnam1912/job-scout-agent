package dev.phoenix;

/** Finds {@code --home DIR} or {@code --home=DIR} in the raw arguments, since the home is needed before parsing. */
final class HomeArgument {

    private static final String FLAG = "--home";

    private HomeArgument() {
    }

    /** @return the directory, or null when the flag is absent or has no value */
    static String find(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(FLAG)) {
                return i + 1 < args.length ? args[i + 1] : null;
            }
            if (args[i].startsWith(FLAG + "=")) {
                return args[i].substring(FLAG.length() + 1);
            }
        }
        return null;
    }
}
