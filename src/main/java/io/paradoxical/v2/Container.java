package io.paradoxical.v2;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

@Data
@RequiredArgsConstructor
public class Container implements AutoCloseable {
    private static final Logger logger = getLogger(Container.class);

    private final CreateContainerResponse containerInfo;
    private final Map<Integer, Integer> targetPortToHostPortLookup;
    private final String dockerHost;
    private final DockerClient client;

    private Boolean isClosed = false;

    public String readLogsFully(Integer waitTimeSeconds) throws InterruptedException {
        final LogContainerCmd logContainerCmd =
                client.logContainerCmd(containerInfo.getId())
                      .withStdOut(true)
                      .withFollowStream(true)
                      .withStdErr(true);

        final StringBuilder stringBuilder = new StringBuilder();

        final LogContainerResultCallback resultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(final Frame item) {
                stringBuilder.append(item.toString() + System.lineSeparator());
            }
        };

        logContainerCmd.exec(resultCallback);

        resultCallback.awaitCompletion(waitTimeSeconds, TimeUnit.SECONDS);

        return stringBuilder.toString();
    }

    public WaitContainerResultCallback waitForCompletion() {
        final WaitContainerResultCallback waitContainerResultCallback = new WaitContainerResultCallback();

        client.waitContainerCmd(getContainerInfo().getId()).exec(waitContainerResultCallback);

        return waitContainerResultCallback;
    }

    @Override
    public void close() {
        try {
            try {
                client.stopContainerCmd(containerInfo.getId()).withTimeout(30).exec();
            }
            catch (Exception ex) {
                // no-op
            }

            client.removeContainerCmd(containerInfo.getId())
                  .withForce(true)
                  .withRemoveVolumes(true)
                  .exec();

            client.close();
        }
        catch (Exception e) {
            // no-op
        }

        isClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (!isClosed) {
            close();
        }
    }
}
