package io.paradoxical;

import com.godaddy.logging.Logger;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.messages.ContainerInfo;
import lombok.Value;

import java.util.Map;

import static com.godaddy.logging.LoggerFactory.getLogger;

@Value
public class Container implements AutoCloseable {
    private static final Logger logger = getLogger(Container.class);

    private final ContainerInfo containerInfo;
    private final Map<Integer, Integer> targetPortToHostPortLookup;
    private final String dockerHost;
    private final DockerClient client;

    @Override
    public void close() {
        try {
            client.stopContainer(containerInfo.id(), 10);
        }
        catch (DockerException | InterruptedException e) {
            logger.error(e, "Error stopping container");
        }

        client.close();
    }
}
