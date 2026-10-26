package com.evp.support;

import com.evp.audit.AuditLogger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures audit events so tests can assert every step was recorded. */
public class RecordingAuditLogger implements AuditLogger {

    public record Event(String type, String actor, Map<String, String> details) {
    }

    private final List<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(String eventType, String actor, Map<String, String> details) {
        events.add(new Event(eventType, actor, Map.copyOf(details)));
    }

    public List<String> types() {
        return events.stream().map(Event::type).toList();
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
