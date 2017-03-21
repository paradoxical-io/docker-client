package io.paradoxical.v2;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.AuthConfigFile;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.spotify.docker.client.exceptions.DockerException;
import io.paradoxical.DockerClientConfig;
import io.paradoxical.EnvironmentVar;
import io.paradoxical.LogMatcher;
import io.paradoxical.MappedPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DockerCreator {
    private static final Logger logger = LoggerFactory.getLogger(io.paradoxical.DockerCreator.class);

    private static Random random = new Random();

    public static Container build(DockerClientConfig config) throws DockerException, InterruptedException {
        return build(config, null);
    }

    public static Container build(DockerClientConfig config, AuthConfig authConfig) throws InterruptedException, DockerException {

        final Ports ports = new Ports();

        for (MappedPort port : config.getMappedPorts()) {
            ports.bind(ExposedPort.tcp(port.getContainerPort()), Ports.Binding.bindPort(port.getHostPort()));
        }

        for (Integer transientPort : config.getTransientPorts()) {
            ports.bind(ExposedPort.tcp(transientPort), Ports.Binding.bindPort(random.nextInt(30000) + 15000));
        }

        final DefaultDockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        final DockerClient client = DockerClientBuilder.getInstance(dockerClientConfig).build();

        final CreateContainerCmd createContainerCmd =
                client.createContainerCmd(config.getImageName())
                      .withPortBindings(ports)
                      .withLxcConf(new LxcConf("icc", "false"))
                      .withEnv(EnvironmentVar.asEnvVars(config.getEnvVars()));

        if (config.getArguments() != null) {
            createContainerCmd.withCmd(config.getArguments().split(" "));
        }

        if (config.getContainerName() != null) {
            createContainerCmd.withName(config.getContainerName());
        }

        if (config.isPullAlways()) {
            final PullImageResultCallback pullImageResultCallback = new PullImageResultCallback();

            final PullImageCmd pullImageCmd = client.pullImageCmd(config.getImageName());

            if (authConfig != null) {
                pullImageCmd.withAuthConfig(authConfig);
            }
            else {
                setAuthFromFile(pullImageCmd, dockerClientConfig, config.getImageName());
            }

            pullImageCmd.exec(pullImageResultCallback);

            pullImageResultCallback.awaitSuccess();
        }

        final CreateContainerResponse containerResponse = createContainerCmd.exec();

        client.startContainerCmd(containerResponse.getId()).exec();

        logger.info("Starting container id " + containerResponse.getId() + ", " + config.getImageName());

        if (config.getWaitForLogLine() != null) {
            waitForContainer(containerResponse, client, config);
        }

        logger.info("Container id " + containerResponse.getId() + " ready");

        return new Container(containerResponse, getMappedPorts(ports), getHost(dockerClientConfig.getDockerHost()), client);
    }

    private static void setAuthFromFile(final PullImageCmd pullImageCmd, final DefaultDockerClientConfig dockerClientConfig, final String imageName) {
        final AuthConfig authConfig = dockerClientConfig.effectiveAuthConfig(imageName);

        if (authConfig != null) {
            pullImageCmd.withAuthConfig(authConfig);
        }
    }

    private static String getHost(final URI dockerHost) {
        if (Objects.equals(dockerHost.getScheme(), "unix")) {
            return "localhost";
        }
        else {
            return dockerHost.getHost();
        }
    }

    private static void waitForContainer(
            final CreateContainerResponse containerResponse,
            final DockerClient client,
            final DockerClientConfig config
    ) throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final LogContainerCmd logContainerCmd =
                client.logContainerCmd(containerResponse.getId())
                      .withStdOut(true)
                      .withFollowStream(true)
                      .withStdErr(true);

        logContainerCmd.exec(new LogContainerResultCallback() {
            @Override
            public void onNext(final Frame item) {
                if (LogMatcher.matches(item.toString(), config.getWaitForLogLine(), config.getMatchFormat())) {
                    countDownLatch.countDown();
                }
            }

            @Override
            public void onComplete() {
                countDownLatch.countDown();
            }
        });

        if (!countDownLatch.await(config.getMaxWaitLogSeconds(), TimeUnit.SECONDS)) {
            logger.warn("Didn't find log line in a timely fashion, continuing");
        }
    }

    private static Map<Integer, Integer> getMappedPorts(final Ports containerPorts) {
        final HashMap<Integer, Integer> ports = new HashMap<>();

        for (final Map.Entry<ExposedPort, Ports.Binding[]> m : containerPorts.getBindings().entrySet()) {
            ports.put(m.getKey().getPort(), Integer.valueOf(m.getValue()[0].getHostPortSpec()));
        }

        return ports;
    }
}
