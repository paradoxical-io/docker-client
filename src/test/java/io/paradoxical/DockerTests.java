package io.paradoxical;

import com.rabbitmq.client.ConnectionFactory;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerException;
import org.junit.Test;

import java.io.IOException;

public class DockerTests {
    @Test
    public void docker_starts() throws InterruptedException, DockerException, DockerCertificateException, IOException {
        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .imageName("rabbitmq:management")
                                  .port(5672)
                                  .waitForLogLine("Server startup complete")
                                  .build();

        try (final Container client = DockerCreator.build(config)) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(client.getDockerHost());
            factory.setPort(client.getTargetPortToHostPortLookup().get(5672));
            factory.newConnection();
        }
    }
}

