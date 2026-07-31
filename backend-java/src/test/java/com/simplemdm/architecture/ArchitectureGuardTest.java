package com.simplemdm.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {

    private static final Path PRODUCTION_SOURCE = Path.of("src", "main", "java", "com", "simplemdm");

    @Test
    void noLegacyPersonnelPersistenceOrRuntimePathsRemain() throws IOException {
        try (var files = Files.walk(PRODUCTION_SOURCE)) {
            List<String> violations = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> violations(path).stream())
                .toList();

            assertThat(violations)
                .as("legacy personnel persistence/runtime references")
                .isEmpty();
        }
    }

    private static List<String> violations(Path path) {
        try {
            String source = Files.readString(path);
            return List.of(
                    "MdmPersonnel",
                    "PersonnelSubService",
                    "PersonnelService",
                    "DynamicFieldService",
                    "owner_dept",
                    "mdm_personnel",
                    "dynamic_data")
                .stream()
                .filter(source::contains)
                .map(term -> path + " -> " + term)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }
}
