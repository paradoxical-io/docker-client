package io.paradoxical;

import lombok.Value;

@Value
public class MappedPort{
    Integer hostPort;
    Integer containerPort;
}
