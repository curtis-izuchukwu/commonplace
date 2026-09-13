package com.commonplace.service;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.TopicRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ModuleTopicService {

    private final ModuleRepository moduleRepository;
    private final TopicRepository topicRepository;
    private final UserSettingsService userSettingsService;

    public ModuleTopicService() {
        this(new ModuleRepository(), new TopicRepository(), new UserSettingsService());
    }

    public ModuleTopicService(ModuleRepository moduleRepository, TopicRepository topicRepository) {
        this(moduleRepository, topicRepository, new UserSettingsService());
    }

    public ModuleTopicService(
            ModuleRepository moduleRepository,
            TopicRepository topicRepository,
            UserSettingsService userSettingsService) {
        this.moduleRepository = moduleRepository;
        this.topicRepository = topicRepository;
        this.userSettingsService = userSettingsService;
    }

    public StudyModule createModule(
            String name, String description, LocalDate examDate, ImportanceLevel importance)
            throws SQLException {

        validateName(name, "Module name");

        return moduleRepository.create(
                name,
                description,
                examDate,
                importance == null ? defaultModulePriority() : importance);
    }

    public List<StudyModule> getAllModules() throws SQLException {
        List<StudyModule> modules = moduleRepository.findAll();

        if (!userSettingsService.load().archiveCompletedModules()) {
            return modules;
        }

        LocalDate today = LocalDate.now();

        return modules.stream()
                .filter(module -> module.examDate() == null || !module.examDate().isBefore(today))
                .toList();
    }

    public void deleteModule(long moduleId) throws SQLException {
        moduleRepository.deleteById(moduleId);
    }

    public Topic createTopic(
            long moduleId,
            String name,
            String description,
            ImportanceLevel importance,
            ConfidenceLevel confidence)
            throws SQLException {

        validateName(name, "Topic name");

        return topicRepository.create(
                moduleId,
                name,
                description,
                importance == null ? ImportanceLevel.MEDIUM : importance,
                confidence == null ? ConfidenceLevel.MEDIUM : confidence);
    }

    public List<Topic> getTopicsForModule(long moduleId) throws SQLException {
        new LearningService().refreshAll();
        return topicRepository.findByModuleId(moduleId);
    }

    public void deleteTopic(long topicId) throws SQLException {
        topicRepository.deleteById(topicId);
    }

    public int countTopicsForModule(long moduleId) throws SQLException {
        return topicRepository.countByModuleId(moduleId);
    }

    public double getAverageMasteryForModule(long moduleId) throws SQLException {
        return new LearningService().moduleMastery(moduleId);
    }

    private void validateName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }

        if (value.trim().length() > 120) {
            throw new IllegalArgumentException(fieldName + " must be 120 characters or fewer.");
        }
    }

    private ImportanceLevel defaultModulePriority() throws SQLException {
        return ImportanceLevel.valueOf(userSettingsService.load().defaultModulePriority());
    }
}
