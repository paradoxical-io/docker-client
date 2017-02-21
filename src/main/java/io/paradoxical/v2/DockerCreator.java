package io.paradoxical.v2;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.spotify.docker.client.exceptions.DockerException;
import io.paradoxical.DockerClientConfig;
import io.paradoxical.EnvironmentVar;
import io.paradoxical.LogMatcher;
import io.paradoxical.MappedPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DockerCreator {
    private static Random random = new Random();

    public static Container build(DockerClientConfig config) throws InterruptedException, DockerException {

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

        final CreateContainerResponse containerResponse = createContainerCmd.exec();

        client.startContainerCmd(containerResponse.getId()).exec();

        if (config.getWaitForLogLine() != null) {
            waitForContainer(containerResponse, client, config);
        }

        return new Container(containerResponse, getMappedPorts(ports), dockerClientConfig.getDockerHost().getHost(), client);
    }

    private static void waitForContainer(
            final CreateContainerResponse containerResponse,
            final DockerClient client,
            final DockerClientConfig config
    ) throws InterruptedException {
        final StringBuilder stringBuilder = new StringBuilder();

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final long[] since = { 0 };

        final LogContainerCmd logContainerCmd =
                client.logContainerCmd(containerResponse.getId())
                      .withStdOut(true)
                      .withStdErr(true);

        long start = System.currentTimeMillis();

        final Boolean[] found = { false };

        while (true) {
            if ((System.currentTimeMillis() - start) / 1000 > config.getMaxWaitLogSeconds()) {
                break;
            }

            logContainerCmd.withSince((int) since[0])
                           .exec(new LogContainerResultCallback() {
                               @Override
                               public void onNext(final Frame item) {
                                   if (LogMatcher.matches(item.toString(), config.getWaitForLogLine(), config.getMatchFormat())) {
                                       found[0] = true;
                                   }

                                   super.onNext(item);
                               }

                               @Override
                               public void onComplete() {
                                   countDownLatch.countDown();
                               }
                           });

            countDownLatch.await();

            if (found[0]) {
                return;
            }

            Thread.sleep(500);
        }
    }

    private static Map<Integer, Integer> getMappedPorts(final Ports containerPorts) {
        final HashMap<Integer, Integer> ports = new HashMap<>();

        containerPorts.getBindings().entrySet().forEach(m -> ports.put(m.getKey().getPort(), Integer.valueOf(m.getValue()[0].getHostPortSpec())));

        return ports;
    }
}
