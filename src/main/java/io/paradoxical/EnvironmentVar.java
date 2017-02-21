package io.paradoxical;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class EnvironmentVar {
    String envVarName;
    String envVarValue;

    public static String asEnvVar(EnvironmentVar var) {
        return var.envVarName + "=" + var.envVarValue;
    }

    public static List<String> asEnvVars(List<EnvironmentVar> envVars) {
        List<String> vars = new ArrayList<>();

        for (final EnvironmentVar envVar : envVars) {
            vars.add(EnvironmentVar.asEnvVar(envVar));
        }

        return vars;
    }
}
