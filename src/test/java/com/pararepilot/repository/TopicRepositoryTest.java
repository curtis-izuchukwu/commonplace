package com.pararepilot.repository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;

class TopicRepositoryTest {

    @Test
    void canCreateAndReadTopicsForModule() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();

        String uniqueName = "Algorithms " + UUID.randomUUID();

        StudyModule module = moduleRepository.create(
                uniqueName,
                "Test module",
                null,
                ImportanceLevel.HIGH
        );

        Topic topic = topicRepository.create(
                module.id(),
                "Binary Search Trees",
                "Traversal, insertion, deletion",
                ImportanceLevel.HIGH,
                ConfidenceLevel.LOW
        );

        List<Topic> topics = topicRepository.findByModuleId(module.id());

        assertTrue(topic.id() > 0);
        assertEquals(module.id(), topic.moduleId());
        assertTrue(topics.stream().anyMatch(savedTopic -> savedTopic.id() == topic.id()));
        assertEquals(1, topicRepository.countByModuleId(module.id()));

        moduleRepository.deleteById(module.id());
    }
}