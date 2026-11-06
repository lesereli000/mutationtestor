package mutationtester.equivalent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs git commands for the equivalent-mutant branch. Unlike Main.runCommand
 * this keeps stdout and stderr apart (stdout is file content), passes
 * arguments without splitting on spaces, and never waits on a credential
 * prompt.
 */
public final class Git {
    private static final long TIMEOUT_SECONDS = 120;

    public record Result(int exitCode, String stdout, String stderr) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private Git() {
    }

    public static Result run(Path dir, String... args) {
        return run(dir, null, Map.of(), args);
    }

    public static Result runWithInput(Path dir, String stdin, String... args) {
        return run(dir, stdin, Map.of(), args);
    }

    public static Result run(Path dir, String stdin, Map<String, String> extraEnv, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        Map<String, String> env = pb.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GCM_INTERACTIVE", "never");
        env.putAll(extraEnv);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return new Result(-1, "", "Could not run git: " + e.getMessage());
        }

        CompletableFuture<String> out = readAsync(process.getInputStream());
        CompletableFuture<String> err = readAsync(process.getErrorStream());

        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // The process may exit before reading stdin; its exit code says what happened.
        }

        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, "", "git " + String.join(" ", args) + " timed out");
            }
            return new Result(process.exitValue(), out.join(), err.join());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Result(-1, "", "Interrupted while running git");
        }
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                stream.transferTo(buffer);
                return buffer.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }
}
