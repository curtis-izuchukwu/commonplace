package com.commonplace.repository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;

class TopicRepositoryTest {

    @Test
    void canCreateAndReadTopicsForModule() throws Exception {
        ModuleRepository moduleRepository = new ModuleRepository();
        TopicRepository topicRepository = new TopicRepository();

        String uniqueName = "Algorithms " + UUID.randomUUID();

        StudyModule module = null;

        try {
            module = moduleRepository.create(
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
            long moduleId = module.id();
            long topicId = topic.id();

            assertTrue(topic.id() > 0);
            assertEquals(moduleId, topic.moduleId());
            assertTrue(topics.stream().anyMatch(savedTopic -> savedTopic.id() == topicId));
            assertEquals(1, topicRepository.countByModuleId(moduleId));
        } finally {
            if (module != null) {
                moduleRepository.deleteById(module.id());
            }
        }
    }
}
