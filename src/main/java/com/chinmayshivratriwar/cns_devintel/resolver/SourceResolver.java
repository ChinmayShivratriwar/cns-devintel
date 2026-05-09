package com.chinmayshivratriwar.cns_devintel.resolver;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

/**
 * Resolves an input string (GitHub URL or local path) into a usable filesystem Path.
 *
 * WHY this exists as a separate class:
 *   Every tool method needs to turn "user input" into a directory it can parse.
 *   Keeping this logic here means the tool layer stays clean — tools just call
 *   resolve() and cleanup() and never touch JGit or file I/O directly.
 */
@Component
public class SourceResolver {

    /**
     * Resolves the input into a Path.
     *
     * If it's a GitHub URL  → shallow-clone into a temp dir, return that path.
     *                          Caller MUST call cleanup(result) when done.
     *
     * If it's a local path  → return Path.of(input) directly.
     *                          Caller should NOT call cleanup() — it's their own filesystem.
     */
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

    /**
     * Deletes the temp directory created during a GitHub clone.
     * Safe to call even if isTemp = false (it no-ops).
     */
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

    // ─── private ──────────────────────────────────────────────────────────────

    private boolean isGitHubUrl(String input) {
        return input.startsWith("https://github.com") || input.startsWith("http://github.com");
    }

    private ResolvedSource cloneFromGitHub(String url) {
        try {
            Path tempDir = Files.createTempDirectory("cns-devintel-");

            // Shallow clone (depth=1) — we only need the latest snapshot, not full history.
            // This is critical for performance: a full clone of a large repo would be slow
            // and we'd be doing this on every tool call.
            Git.cloneRepository()
                    .setURI(normalizeUrl(url))
                    .setDirectory(tempDir.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .call()
                    .close();

            return new ResolvedSource(tempDir, true);

        } catch (Exception e) {
            throw new RuntimeException("Failed to clone repository: " + url + " — " + e.getMessage(), e);
        }
    }

    /**
     * Normalizes GitHub URLs:
     *   https://github.com/user/repo        → keep as-is (already valid git URL)
     *   https://github.com/user/repo/tree/main → strip to base repo URL
     */
    private String normalizeUrl(String url) {
        // strip trailing .git if missing
        String base = url.split("/tree/")[0]   // remove /tree/branch
                .split("/blob/")[0];  // remove /blob/file

        if (!base.endsWith(".git")) {
            base = base + ".git";
        }
        return base;
    }

    /**
     * Simple record to carry the resolved path and whether it's a temp dir.
     * isTemp=true  → caller must call cleanup() when done
     * isTemp=false → local path, caller owns it, don't delete
     */
    public record ResolvedSource(Path path, boolean isTemp) {}
}
