package com.commonplace.service;

import java.sql.SQLException;
import java.util.List;

import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.repository.ModuleRepository;
import com.commonplace.repository.TopicRepository;

public class StudyStructureService {

    private final ModuleRepository moduleRepository;
    private final TopicRepository topicRepository;

    public StudyStructureService() {
        this(new ModuleRepository(), new TopicRepository());
    }

    public StudyStructureService(
            ModuleRepository moduleRepository,
            TopicRepository topicRepository
    ) {
        this.moduleRepository = moduleRepository;
        this.topicRepository = topicRepository;
    }

    public List<StudyModule> getModules() throws SQLException {
        return moduleRepository.findAll();
    }

    public List<Topic> getTopicsForModule(long moduleId) throws SQLException {
        return topicRepository.findByModuleId(moduleId);
    }
}
