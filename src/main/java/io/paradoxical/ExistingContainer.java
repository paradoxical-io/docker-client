package io.paradoxical;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerInfo;
import lombok.Value;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

@Value
public class ExistingContainer implements AutoCloseable {
    private final ContainerInfo containerInfo;
    private final DockerClient client;

    private static final Logger logger = getLogger(Container.class);

    @Override
    public void close() {
        try {
            client.stopContainer(containerInfo.id(), 10);

            client.removeContainer(containerInfo.id());
        }
        catch (DockerException | InterruptedException e) {
            logger.error("Error stopping container", e);
        }
    }
}
