package io.paradoxical.v2;

import com.github.dockerjava.api.command.CreateContainerCmd;

public  interface ContainerConfigurator {
    void configure(CreateContainerCmd cmd);
}
