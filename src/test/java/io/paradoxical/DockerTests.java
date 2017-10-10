package io.paradoxical;

import com.rabbitmq.client.ConnectionFactory;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
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
    public void test_elasticsearch() throws InterruptedException, DockerCertificateException, DockerException {
        final Container one = DockerCreator.build(DockerClientConfig.builder()
                                                                    .imageName("elasticsearch:1.5.2")
                                                                    .waitForLogLine("started")
                                                                    .port(9200)
                                                                    .build());

        final Container two = DockerCreator.build(DockerClientConfig.builder()
                                                                    .imageName("elasticsearch:1.5.2")
                                                                    .waitForLogLine("started")
                                                                    .port(9200)
                                                                    .build());

        Thread.sleep(500);

        one.close();
        two.close();
    }

    @Test
    public void regex_works() throws InterruptedException, DockerException, DockerCertificateException, IOException {
        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .imageName("vsouza/sqs-local")
                                  .port(5672)
                                  .pullAlways(true)
                                  .waitForLogLine("ElasticMQ server \\(.+\\) started", LogLineMatchFormat.Regex)
                                  .build();

        try (final Container client = DockerCreator.build(config)) {

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

    @Test
    public void docker_redis() throws InterruptedException, DockerException, DockerCertificateException, IOException {

        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .imageName("redis:2.8.23")
                                  .waitForLogLine("The server is now ready to accept connections on port 6379", LogLineMatchFormat.Exact, 5)
                                  .port(6379)
                                  .build();

        try (final Container client = DockerCreator.build(config)) {
            System.out.println(client);
        }
    }

    @Test
    public void can_list_prefixed_containers() throws InterruptedException, DockerException, DockerCertificateException {
        for (final ExistingContainer dynamo : DockerCreator.findContainers("dynamo")) {
            dynamo.close();
        }

        final Container dynamo1 = DockerCreator.build(
                DockerClientConfig.builder()
                                  .imageName("vsouza/dynamo-local")
                                  .containerName("dynamo1")
                                  .port(8080)
                                  .waitForLogLine("Listening at")
                                  .arguments("--port 8080 --createTableMs 5 --deleteTableMs 5 --updateTableMs 5")
                                  .build());

        final Container dynamo2 = DockerCreator.build(
                DockerClientConfig.builder()
                                  .imageName("vsouza/dynamo-local")
                                  .containerName("dynamo2")
                                  .port(8080)
                                  .waitForLogLine("Listening at")
                                  .arguments("--port 8080 --createTableMs 5 --deleteTableMs 5 --updateTableMs 5")
                                  .build());

        final List<ExistingContainer> dynamo = DockerCreator.findContainers(dynamo1.getClient(), "dynamo");

        assertThat(dynamo.size()).isEqualTo(2);

        for (final ExistingContainer container : dynamo) {
            container.close();
        }

        final List<ExistingContainer> dynamoAfter = DockerCreator.findContainers(dynamo1.getClient(), "dynamo");

        assertThat(dynamoAfter.size()).isEqualTo(0);
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

