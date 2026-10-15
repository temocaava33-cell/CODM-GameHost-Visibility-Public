package com.codm.gamehost.offline;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShellService extends ICommandService.Stub {
    public ShellService() {}

    @Override
    public String execute(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 48000) {
                        output.append(line).append('\n');
                    }
                }
            }
            int exitCode = process.waitFor();
            output.append("\nExit code: ").append(exitCode);
        } catch (Exception e) {
            output.append("ERROR: ").append(e.getClass().getSimpleName())
                  .append(": ").append(e.getMessage());
        }
        return output.toString();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
