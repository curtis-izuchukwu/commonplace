package com.pararepilot.repository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;

class ModuleRepositoryTest {

    @Test
    void canCreateAndReadModule() throws Exception {
        ModuleRepository repository = new ModuleRepository();
        StudyModule created = null;

        try {
            created = repository.create(
                    "Algorithms",
                    "Sorting, graphs, dynamic programming",
                    null,
                    ImportanceLevel.HIGH
            );

            assertTrue(created.id() > 0);
            assertEquals("Algorithms", created.name());
            assertEquals(ImportanceLevel.HIGH, created.importance());

            List<StudyModule> modules = repository.findAll();
            long createdId = created.id();

            assertTrue(
                    modules.stream().anyMatch(module -> module.id() == createdId),
                    "Created module should appear in findAll()"
            );
        } finally {
            if (created != null) {
                repository.deleteById(created.id());
            }
        }
    }
}
