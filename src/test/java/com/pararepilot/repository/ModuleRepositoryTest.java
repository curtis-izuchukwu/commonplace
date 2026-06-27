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

        StudyModule created = repository.create(
                "Algorithms",
                "Sorting, graphs, dynamic programming",
                null,
                ImportanceLevel.HIGH
        );

        assertTrue(created.id() > 0);
        assertEquals("Algorithms", created.name());
        assertEquals(ImportanceLevel.HIGH, created.importance());

        List<StudyModule> modules = repository.findAll();

        assertTrue(
                modules.stream().anyMatch(module -> module.id() == created.id()),
                "Created module should appear in findAll()"
        );
    }
}