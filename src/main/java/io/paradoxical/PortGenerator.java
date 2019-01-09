package io.paradoxical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class PortGenerator {
    private static final Logger logger = LoggerFactory.getLogger(PortGenerator.class);
    private static final Random random = new Random();

    public static int getNextAvailablePort() {
        int port = random.nextInt(30000) + 15000;
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            serverSocket.close();
        } catch (IOException e) {
            logger.warn("Unable to acquire an available port from the OS, falling back to a random port.");
        }

        return port;
    }
}
