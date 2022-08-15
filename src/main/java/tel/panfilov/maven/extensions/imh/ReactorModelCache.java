package tel.panfilov.maven.extensions.imh;

import org.apache.maven.model.building.ModelCache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ReactorModelCache implements ModelCache {

    Map<String, Map<String, Map<String, Map<String, Object>>>> cache = new ConcurrentHashMap<>();

    public Object get(String groupId, String artifactId, String version, String tag) {
        return Optional.ofNullable(cache.get(groupId))
                .map(m -> m.get(artifactId))
                .map(m -> m.get(version))
                .map(m -> m.get(tag))
                .orElse(null);
    }

    public void put(String groupId, String artifactId, String version, String tag, Object data) {
        cache.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(artifactId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(version, k -> new ConcurrentHashMap<>())
                .put(tag, data);
    }

}