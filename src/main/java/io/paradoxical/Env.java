package io.paradoxical;

import java.io.File;

public class Env {
    public static boolean isDockerNative() {
        return new File("/var/run/docker.sock").exists();
    }

    public static boolean isUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("aix");
    }
}
