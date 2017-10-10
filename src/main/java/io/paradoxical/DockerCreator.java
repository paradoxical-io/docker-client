package io.paradoxical;

import com.google.common.base.Splitter;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogMessage;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.RegistryAuth;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.spotify.docker.client.DockerClient.AttachParameter.LOGS;
import static com.spotify.docker.client.DockerClient.AttachParameter.STDERR;
import static com.spotify.docker.client.DockerClient.AttachParameter.STDOUT;
import static com.spotify.docker.client.DockerClient.AttachParameter.STREAM;

/**
 * Please use the v2 namespace
 */
@Deprecated()
public class DockerCreator {
    private static final Logger logger = LoggerFactory.getLogger(DockerCreator.class);

    private static final Random random = new Random();

    public static Container build(DockerClientConfig config) throws InterruptedException, DockerException {
        return new DockerCreator().create(config);
    }

    public static List<ExistingContainer> findContainers(String prefixedWith) throws InterruptedException, DockerException {
        final DockerClient dockerClient = defaultClient();

        return findContainers(dockerClient, prefixedWith);
    }

    public static List<ExistingContainer> findContainers(DockerClient dockerClient, String prefixedWith) throws InterruptedException, DockerException {
        final List<com.spotify.docker.client.messages.Container> allContainers =
                dockerClient.listContainers(DockerClient.ListContainersParam.allContainers(true));

        final List<ExistingContainer> foundContainers = new ArrayList<>();

        for (final com.spotify.docker.client.messages.Container container : allContainers) {
            if (containerStartsWith(container, prefixedWith)) {
                final ContainerInfo containerInfo = dockerClient.inspectContainer(container.id());

                final ExistingContainer containerMeta = new ExistingContainer(containerInfo, dockerClient);

                foundContainers.add(containerMeta);
            }
        }

        return foundContainers;
    }

    private static boolean containerStartsWith(final com.spotify.docker.client.messages.Container container, final String prefixedWith) {
        for (final String s : container.names()) {
            if (s.substring(1, s.length()).startsWith(prefixedWith)) {
                return true;
            }
        }

        return false;
    }

    public Container create(DockerClientConfig config) throws DockerException, InterruptedException {

        Map<String, List<PortBinding>> portBindings = new HashMap<>();

        for (Integer port : config.getTransientPorts()) {
            portBindings.put(port.toString(), Collections.singletonList(PortBinding.of("0.0.0.0", random.nextInt(30000) + 15000)));
        }

        for (MappedPort mappedPort : config.getMappedPorts()) {
            portBindings.put(mappedPort.getHostPort().toString(),
                             Collections.singletonList(PortBinding.of("0.0.0.0", mappedPort.getContainerPort().toString())));
        }

        HostConfig hostConfig = HostConfig.builder()
                                          .portBindings(portBindings)
                                          .lxcConf(new HostConfig.LxcConfParameter() {
                                              @Override
                                              public String key() {
                                                  return "icc";
                                              }

                                              @Override
                                              public String value() {
                                                  return "false";
                                              }
                                          })
                                          .build();

        ContainerConfig.Builder configBuilder =
                ContainerConfig.builder()
                               .hostConfig(hostConfig)
                               .image(config.getImageName())
                               .env(getEnvVars(config.getEnvVars()))
                               .exposedPorts(getPorts(config.getTransientPorts()));

        if (config.getArguments() != null) {
            configBuilder.cmd(Splitter.on(' ').splitToList(config.getArguments()));
        }

        addCustomConfigs(configBuilder);

        final ContainerConfig container = configBuilder.build();

        final DockerClient client = createDockerClient(config);

        try {
            if (config.isPullAlways()) {
                client.pull(container.image());
            }
            else {
                client.inspectImage(container.image());
            }
        }
        catch (Exception e) {
            client.pull(container.image());
        }

        final ContainerCreation createdContainer = client.createContainer(container, config.getContainerName());

        client.startContainer(createdContainer.id());

        if (!StringUtils.isEmpty(config.getWaitForLogLine())) {
            waitForLogInContainer(createdContainer, client, config);
        }

        final ContainerInfo containerInfo = client.inspectContainer(createdContainer.id());

        Map<Integer, Integer> targetPortToHostPortLookup = new HashMap<>();

        for (final Integer port : config.getTransientPorts()) {
            targetPortToHostPortLookup.put(
                    port,
                    Integer.parseInt(containerInfo.networkSettings()
                                                  .ports()
                                                  .get(port + "/tcp")
                                                  .get(0)
                                                  .hostPort())
            );
        }

        return new Container(containerInfo, targetPortToHostPortLookup, client.getHost(), client);
    }

    private List<String> getEnvVars(final List<EnvironmentVar> envVars) {
        return EnvironmentVar.asEnvVars(envVars);
    }

    private String[] getPorts(final List<Integer> ports) {
        Set<String> portsSet = new HashSet<>();
        for (final Integer port : ports) {
            portsSet.add(port.toString());
        }

        return portsSet.toArray(new String[]{});
    }

    protected void addCustomConfigs(final ContainerConfig.Builder configBuilder) {
        // extension point
    }

    protected void waitForLogInContainer(
            final ContainerCreation createdContainer,
            final DockerClient client,
            final DockerClientConfig config)
            throws DockerException, InterruptedException {

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        // wrap waiting for the log in a dedicated thread, since the docker client
        // sometimes misses log lines and waits forever
        Thread threadHack =
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final long start = System.currentTimeMillis();

                        String log = "";
                        LogStream logs = null;
                        try {
                            logs = client.attachContainer(createdContainer.id(), LOGS, STREAM, STDOUT, STDERR);
                        }
                        catch (DockerException | InterruptedException e) {
                            e.printStackTrace();

                            return;
                        }
                        do {
                            if ((System.currentTimeMillis() - start) / 1000 > config.getMaxWaitLogSeconds()) {
                                return;
                            }

                            if (!logs.hasNext()) {
                                try {
                                    Thread.sleep(10);
                                }
                                catch (InterruptedException e) {

                                }

                                continue;
                            }

                            LogMessage logMessage = logs.next();
                            ByteBuffer buffer = logMessage.content();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            log += new String(bytes);
                        } while (!LogMatcher.matches(log, config.getWaitForLogLine(), config.getMatchFormat()));

                        countDownLatch.countDown();
                    }
                });

        threadHack.setDaemon(true);

        threadHack.start();

        if (!countDownLatch.await(config.getMaxWaitLogSeconds(), TimeUnit.SECONDS)) {
            logger.warn("Log line never appeared in a timeline manner, attempting to continue");
        }
    }

    public static DockerClient defaultClient() {
        return createDockerClient(DockerClientConfig.builder().build());
    }

    protected static DockerClient createDockerClient(DockerClientConfig config) {
        final RegistryAuth registryAuth = getRegistryAuth(config);

        try {
            if (registryAuth != null) {
                return DefaultDockerClient.fromEnv().registryAuth(registryAuth).build();
            }
            else {
                return DefaultDockerClient.fromEnv().build();
            }
        }
        catch (DockerCertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static RegistryAuth getRegistryAuth(final DockerClientConfig config) {
        try {
            final String[] segments = config.getImageName().split("\\/");

            if (segments.length > 0) {
                try {
                    return RegistryAuth.fromDockerConfig(segments[0]).build();
                }
                catch (IOException e) {
                    return null;
                }
            }
        }
        catch (Exception ex) {
            return null;
        }

        return null;
    }
}
