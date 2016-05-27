package io.paradoxical;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
public class DockerClientConfig {
    public static String DOCKER_MACHINE_SERVICE_URL = "https://192.168.99.100:2376";

    private String dockerMachineUrl = DOCKER_MACHINE_SERVICE_URL;

    private String imageName;

    private String waitForLogLine;

    private String arguments = null;

    private List<Integer> transientPorts;

    private List<MappedPort> mappedPorts;

    private List<EnvironmentVar> envVars;

    private boolean pullAlways = false;

    public static DockerClientConfigBuilder builder() {return new DockerClientConfigBuilder();}

    public static class DockerClientConfigBuilder {
        private String dockerMachineUrl;
        private String imageName;
        private String waitForLogLine;
        private String arguments;
        private List<Integer> ports = new ArrayList<>();
        private List<MappedPort> mappedPorts = new ArrayList<>();
        private List<EnvironmentVar> envVars = new ArrayList<>();
        private boolean pullAlways;

        DockerClientConfigBuilder() {}

        public DockerClientConfig.DockerClientConfigBuilder dockerMachineUrl(final String dockerMachineUrl) {
            this.dockerMachineUrl = dockerMachineUrl;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder imageName(final String imageName) {
            this.imageName = imageName;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder arguments(final String arguments) {
            this.arguments = arguments;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder waitForLogLine(final String waitForLogLine) {
            this.waitForLogLine = waitForLogLine;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder ports(final List<Integer> ports) {
            this.ports = ports;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder envVars(final List<EnvironmentVar> envVars) {
            this.envVars = envVars;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder mappedPorts(final List<MappedPort> mappedPorts) {
            this.mappedPorts = mappedPorts;
            return this;
        }

        public DockerClientConfig.DockerClientConfigBuilder port(final Integer port) {
            return ports(Arrays.asList(port));
        }

        public DockerClientConfig.DockerClientConfigBuilder mappedPort(final MappedPort port) {
            return mappedPorts(Arrays.asList(port));
        }

        public DockerClientConfig.DockerClientConfigBuilder pullAlways(final boolean pullAlways) {
            this.pullAlways = pullAlways;
            return this;
        }

        public DockerClientConfig build() {
            return new DockerClientConfig(dockerMachineUrl == null ? DOCKER_MACHINE_SERVICE_URL : dockerMachineUrl,
                                          imageName,
                                          waitForLogLine,
                                          arguments,
                                          ports,
                                          mappedPorts,
                                          envVars,
                                          pullAlways);
        }
    }
}
