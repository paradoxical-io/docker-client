package io.paradoxical;

import java.util.regex.Pattern;

public class LogMatcher {
    public static boolean matches(final String log, final String waitForLog, final LogLineMatchFormat matchFormat) {
        switch (matchFormat) {
            case Exact:
                return log.contains(waitForLog);
            case Regex:
                return Pattern.compile(waitForLog).matcher(log).find();
        }

        return false;
    }
}
