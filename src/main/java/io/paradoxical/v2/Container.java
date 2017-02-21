package io.paradoxical.v2;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import io.paradoxical.LogMatcher;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;

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

    public String readLogsFully() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final LogContainerCmd logContainerCmd =
                client.logContainerCmd(containerInfo.getId())
                      .withStdOut(true)
                      .withFollowStream(true)
                      .withStdErr(true);

        final StringBuilder stringBuilder = new StringBuilder();

        logContainerCmd.exec(new LogContainerResultCallback() {
            @Override
            public void onNext(final Frame item) {
                stringBuilder.append(item.toString());
            }

            @Override
            public void onComplete() {
                countDownLatch.countDown();
            }
        });

        return stringBuilder.toString();
    }

    @Override
    public void close() {
        try {
            try {
                client.stopContainerCmd(containerInfo.getId()).withTimeout(30).exec();
            }
            catch (Exception ex) {
                logger.warn("Error stopping container", ex);
            }

            client.removeContainerCmd(containerInfo.getId()).exec();

            client.close();
        }
        catch (Exception e) {
            logger.warn("Error closing container", e);
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
