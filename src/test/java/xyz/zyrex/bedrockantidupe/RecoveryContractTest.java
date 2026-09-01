package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryContractTest {
    @Test
    void recoveryDirectoryUsesExplicitStateFiles() {
        Path root = Path.of("target", "recovery-contract");
        assertDoesNotThrow(() -> Files.createDirectories(root));
        assertTrue(root.toFile().isDirectory());
        assertTrue(Path.of(root.toString(), "sample.recovery").getFileName().toString().endsWith(".recovery"));
        assertTrue(Path.of(root.toString(), "sample.restoring").getFileName().toString().endsWith(".restoring"));
        assertTrue(Path.of(root.toString(), "sample.restored").getFileName().toString().endsWith(".restored"));
    }
}
