package io.paradoxical.v2;

import com.github.dockerjava.api.command.CreateContainerCmd;

public class NullConfigurator implements ContainerConfigurator {
    @Override
    public void configure(final CreateContainerCmd cmd) {

    }
}
