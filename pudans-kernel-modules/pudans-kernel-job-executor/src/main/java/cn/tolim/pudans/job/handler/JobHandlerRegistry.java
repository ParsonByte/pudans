package cn.tolim.pudans.job.handler;

import cn.tolim.pudans.job.dto.JobHandlerDescriptor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Registry of all JobHandler beans available in the application
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> jobHandlers) {
        Map<String, JobHandler> discovered = new LinkedHashMap<>();
        for (JobHandler handler : jobHandlers) {
            JobHandler previous = discovered.putIfAbsent(handler.name(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate job handler name '" + handler.name()
                        + "' used by " + previous.getClass().getName() + " and "
                        + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(discovered);
    }

    // Finds a handler by its registered name
    public Optional<JobHandler> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(name));
    }

    // Checks whether a handler name is registered
    public boolean contains(String name) {
        return name != null && handlers.containsKey(name);
    }

    // All registered handler names, sorted alphabetically
    public List<String> names() {
        return handlers.keySet().stream().sorted().toList();
    }

    // Descriptors for all registered handlers
    public List<JobHandlerDescriptor> descriptors() {
        return handlers.values().stream()
                .map(JobHandlerDescriptor::from)
                .sorted(Comparator.comparing(JobHandlerDescriptor::name))
                .toList();
    }
}