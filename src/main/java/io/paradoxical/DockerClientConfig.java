package io.paradoxical;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DockerClientConfig {
    public static String DOCKER_MACHINE_SERVICE_URL = "https://192.168.99.100:2376";

    private String dockerMachineUrl = DOCKER_MACHINE_SERVICE_URL;

    private String imageName;

    private String waitForLogLine;

    private String arguments = null;

    private List<Integer> ports
}
