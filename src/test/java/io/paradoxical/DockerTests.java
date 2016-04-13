package io.paradoxical;

import com.rabbitmq.client.ConnectionFactory;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerException;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    public void docker_arguments_pass() throws InterruptedException, DockerException, DockerCertificateException {
        try (final Container client = DockerCreator.build(
                DockerClientConfig.builder()
                                  .imageName("vsouza/dynamo-local")
                                  .port(8080)
                                  .waitForLogLine("Listening at")
                                  .arguments("--port 8080 --createTableMs 5 --deleteTableMs 5 --updateTableMs 5")
                                  .build()
        )) {
            assertThat(portIsOpen(client.getDockerHost(), client.getTargetPortToHostPortLookup().get(8080))).isTrue();
        }
    }

    public boolean portIsOpen(String ip, int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 50);
            socket.close();
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }
}

