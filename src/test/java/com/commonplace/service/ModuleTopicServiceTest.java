package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.User;
import com.commonplace.repository.DatabaseManager;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

class ModuleTopicServiceTest {
    private final ModuleRepository modules = new ModuleRepository();
    private final ModuleTopicService service = new ModuleTopicService();
    private User user;

    @BeforeEach
    void signIn() throws Exception {
        user = new UserRepository().create("module-dates-" + UUID.randomUUID(), "hash", "salt");
        AccountSession.signIn(user);
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (var connection = DatabaseManager.connect();
                var statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            statement.setLong(1, user.id());
            statement.executeUpdate();
        } finally {
            AccountSession.signOut();
        }
    }

    @Test
    void changingExamDatePersistsWithoutChangingOtherModuleDetails() throws Exception {
        StudyModule original =
                modules.create(
                        "Algorithms", "Sorting and graphs", LocalDate.now().plusDays(30),
                        ImportanceLevel.HIGH);
        LocalDate newDate = LocalDate.now().plusDays(60);

        StudyModule updated = service.updateModuleExamDate(original.id(), newDate);

        assertEquals(updated, modules.findById(original.id()).orElseThrow());
        assertEquals(newDate, updated.examDate());
        assertEquals(original.name(), updated.name());
        assertEquals(original.description(), updated.description());
        assertEquals(original.importance(), updated.importance());
        assertEquals(original.createdAt(), updated.createdAt());
    }

    @Test
    void examDateCanBeAddedAndRemoved() throws Exception {
        StudyModule module = modules.create("Algorithms", "", null, ImportanceLevel.MEDIUM);
        LocalDate examDate = LocalDate.now().plusDays(7);

        assertEquals(examDate, service.updateModuleExamDate(module.id(), examDate).examDate());
        assertEquals(examDate, modules.findById(module.id()).orElseThrow().examDate());

        assertNull(service.updateModuleExamDate(module.id(), null).examDate());
        assertNull(modules.findById(module.id()).orElseThrow().examDate());
    }

    @Test
    void deletedModuleCannotBeUpdated() throws Exception {
        StudyModule module = modules.create("Algorithms", "", null, ImportanceLevel.MEDIUM);
        modules.deleteById(module.id());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateModuleExamDate(module.id(), LocalDate.now()));
        assertTrue(modules.findById(module.id()).isEmpty());
    }

    @Test
    void examDateCannotBeChangedFromAnotherAccount() throws Exception {
        LocalDate examDate = LocalDate.now().plusDays(30);
        StudyModule module =
                modules.create("Algorithms", "", examDate, ImportanceLevel.MEDIUM);
        AccountSession.signOut();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateModuleExamDate(module.id(), null));

        AccountSession.signIn(user);
        assertEquals(examDate, modules.findById(module.id()).orElseThrow().examDate());
    }
}
