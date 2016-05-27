package io.paradoxical;

import lombok.Value;

@Value
public class EnvironmentVar{
    String envVarName;
    String envVarValue;
}
