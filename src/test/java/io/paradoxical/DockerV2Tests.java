package io.paradoxical.v2;

import com.rabbitmq.client.ConnectionFactory;
import io.paradoxical.DockerClientConfig;
import io.paradoxical.LogLineMatchFormat;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DockerV2Tests {
    @Test
    public void docker_starts() throws Exception {
        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .imageName("rabbitmq:management")
                                  .port(5672)
                                  .pullAlways(true)
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
    public void get_logs() throws Exception {
        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .imageName("java:8")
                                  .port(5672)
                                  .pullAlways(true)
                                  .arguments("echo foo")
                                  .build();

        final Container client = DockerCreator.build(config);

        client.waitForCompletion().awaitCompletion(10, TimeUnit.SECONDS);

        String logs = client.readLogsFully(30);

        assert (logs.contains("foo"));

        client.close();
    }

    @Test
    public void test_elasticsearch() throws Exception {
        final Container one = DockerCreator.build(DockerClientConfig.builder()
                                                                    .imageName("elasticsearch:1.5.2")
                                                                    .waitForLogLine("started")
                                                                    .pullAlways(true)
                                                                    .port(9200)
                                                                    .build());

        final Container two = DockerCreator.build(DockerClientConfig.builder()
                                                                    .imageName("elasticsearch:1.5.2")
                                                                    .waitForLogLine("started")
                                                                    .pullAlways(true)
                                                                    .port(9200)
                                                                    .build());

        Thread.sleep(500);

        one.close();
        two.close();
    }

    @Test
    public void regex_works() throws Exception {
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
    public void docker_arguments_pass() throws Exception {
        try (final Container client = DockerCreator.build(
                DockerClientConfig.builder()
                                  .imageName("vsouza/dynamo-local")
                                  .port(8080)
                                  .pullAlways(true)
                                  .waitForLogLine("Listening at")
                                  .arguments("--port 8080 --createTableMs 5 --deleteTableMs 5 --updateTableMs 5")
                                  .build()
        )) {
            assertThat(portIsOpen(client.getDockerHost(), client.getTargetPortToHostPortLookup().get(8080))).isTrue();
        }
    }

    @Test
    public void docker_redis() throws Exception {

        final DockerClientConfig config =
                DockerClientConfig.builder()
                                  .pullAlways(true)
                                  .imageName("redis:2.8.23")
                                  .waitForLogLine("The server is now ready to accept connections on port 6379", LogLineMatchFormat.Exact, 5)
                                  .port(6379)
                                  .build();

        try (final Container client = DockerCreator.build(config)) {
            System.out.println(client);
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
