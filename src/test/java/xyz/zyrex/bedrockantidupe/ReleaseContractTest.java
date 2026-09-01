package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseContractTest {
    @Test
    void requiredReleaseResourcesExist() {
        Path root = Path.of("src/main/resources");
        assertTrue(Files.isRegularFile(root.resolve("plugin.yml")));
        assertTrue(Files.isRegularFile(root.resolve("config.yml")));
        assertTrue(Files.isRegularFile(root.resolve("messages.yml")));
        assertTrue(Files.isRegularFile(Path.of("pom.xml")));
    }
}
