package com.chinmayshivratriwar.cns_devintel.resolver;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

@Component
public class SourceResolver {

    public ResolvedSource resolve(String input) {
        String trimmed = input.trim();

        if (isGitHubUrl(trimmed)) {
            return cloneFromGitHub(trimmed);
        }

        // local path — just wrap it
        Path local = Path.of(trimmed);
        if (!Files.exists(local)) {
            throw new IllegalArgumentException("Local path does not exist: " + trimmed);
        }
        return new ResolvedSource(local, false);
    }

    public void cleanup(ResolvedSource source) {
        if (!source.isTemp()) return;

        try {
            Files.walkFileTree(source.path(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // best-effort cleanup — log but don't throw
            System.err.println("[SourceResolver] Cleanup failed for: " + source.path() + " — " + e.getMessage());
        }
    }


    private boolean isGitHubUrl(String input) {
        return input.startsWith("https://github.com") || input.startsWith("http://github.com");
    }

    private static final int CLONE_TIMEOUT_SECONDS = 30;

    private ResolvedSource cloneFromGitHub(String url) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("cns-devintel-");

            // Shallow clone (depth=1) — latest snapshot only, not full history.
            //
            // FIX 2 — Clone timeout.
            // Without setTimeout(), a large repo or flaky network hangs the thread
            // forever and the calling agent's tool invocation never returns.
            // setTimeout() sets JGit's transport-level socket timeout in seconds.
            Git.cloneRepository()
                    .setURI(normalizeUrl(url))
                    .setDirectory(tempDir.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .setTimeout(CLONE_TIMEOUT_SECONDS)
                    .call()
                    .close();

            return new ResolvedSource(tempDir, true);

        } catch (Exception e) {
            // cleanup the temp dir we already created before rethrowing
            if (tempDir != null) cleanup(new ResolvedSource(tempDir, true));
            throw new RuntimeException("Failed to clone: " + url
                    + " (timeout=" + CLONE_TIMEOUT_SECONDS + "s) — " + e.getMessage(), e);
        }
    }


    private String normalizeUrl(String url) {
        // strip trailing .git if missing
        String base = url.split("/tree/")[0]   // remove /tree/branch
                .split("/blob/")[0];  // remove /blob/file

        if (!base.endsWith(".git")) {
            base = base + ".git";
        }
        return base;
    }


    public record ResolvedSource(Path path, boolean isTemp) {}
}