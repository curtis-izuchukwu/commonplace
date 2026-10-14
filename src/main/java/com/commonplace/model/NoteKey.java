package com.commonplace.model;

/** A null topic identifies the module's Overview. Names are never document identifiers. */
public record NoteKey(long moduleId, Long topicId) {
    public NoteKey {
        if (moduleId <= 0 || (topicId != null && topicId <= 0)) {
            throw new IllegalArgumentException("A note needs a valid module/topic ID.");
        }
    }

    public static NoteKey overview(long moduleId) { return new NoteKey(moduleId, null); }
    public static NoteKey topic(long moduleId, long topicId) { return new NoteKey(moduleId, topicId); }
    public boolean isOverview() { return topicId == null; }
}
