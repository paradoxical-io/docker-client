docker-client
========================

![Build status](https://travis-ci.org/paradoxical-io/docker-client.svg?branch=master)

A simplified docker client that wraps the spotify client with simpler options that always generates
random transientPorts for host proxying.  Great for testing!

To install

```
<dependency>
    <groupId>io.paradoxical</groupId>
    <artifactId>docker-client</artifactId>
    <version>1.0</version>
</dependency>
```

Usage

```
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
```

