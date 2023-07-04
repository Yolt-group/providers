package com.yolt.providers.web.threadinfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.logstash.logback.marker.Markers.append;

@Slf4j
@RequiredArgsConstructor
class ThreadInfoService {

    static final String STARTUP_LOG = "Attempting to grab thread dump like info. Please check rdd for more info";
    private static final Marker RDD_MARKER = append("raw-data", "true");

    private final Supplier<Map<Thread, StackTraceElement[]>> threadSupplier;

    public void logQuasiThreadDump(String regex) {
        log.info(STARTUP_LOG);
        threadSupplier
                .get()
                .entrySet()
                .stream()
                .filter(threadEntry -> filterThreadEntry(regex, threadEntry))
                .map(this::mapToThreadDumpLikeStructure)
                .forEach(ThreadInfoService::logIntoRdd);
    }

    private boolean filterThreadEntry(String regex, Map.Entry<Thread, StackTraceElement[]> threadEntry) {
        if (regex == null || regex.isBlank()) {
            return true;
        }
        var threadName = threadEntry.getKey().getName();
        return threadName.matches(regex);
    }

    private ThreadInfo mapToThreadDumpLikeStructure(Map.Entry<Thread, StackTraceElement[]> entry) {
        var thread = entry.getKey();
        var elements = entry.getValue();
        var state = thread.getState().name();
        var stackTraceElements = Arrays.stream(elements)
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());
        return new ThreadInfo(thread.toString(), state, stackTraceElements);
    }

    private static void logIntoRdd(ThreadInfo threadInfo) {
        log.debug(RDD_MARKER, "Dumped ThreadInfoObject: {}", threadInfo);
    }
}
