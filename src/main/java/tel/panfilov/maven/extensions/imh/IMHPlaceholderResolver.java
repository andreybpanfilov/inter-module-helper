package tel.panfilov.maven.extensions.imh;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Component(role = IMHPlaceholderResolver.class, hint = "imh")
public class IMHPlaceholderResolver {

    public static final String PLACEHOLDER_RESOLUTION_ENABLED_FLAG = "imh.placeholders";

    public static final String PLACEHOLDER_START = "$D{";

    public static final String PLACEHOLDER_END = "}";

    @Requirement
    protected ArtifactHandlerManager artifactHandlerManager;

    @Requirement
    private RepositorySystem repositorySystem;

    @Requirement
    protected IMHWorkspaceReader workspaceReader;

    @Requirement
    private Logger logger;

    public void resolvePlaceholders(ProjectBuildingRequest request, ProjectBuildingResult result) throws ProjectBuildingException {
        if (!isPlaceholderResolutionEnabled(request, result)) {
            return;
        }

        MavenProject project = result.getProject();
        if (project == null) {
            return;
        }

        for (Plugin plugin : project.getBuildPlugins()) {
            traverseDom(request, project, (Xpp3Dom) plugin.getConfiguration());
            for (PluginExecution execution : plugin.getExecutions()) {
                traverseDom(request, project, (Xpp3Dom) execution.getConfiguration());
            }
        }
    }

    protected void traverseDom(ProjectBuildingRequest request, MavenProject project, Xpp3Dom dom) throws ProjectBuildingException {
        if (dom == null) {
            return;
        }

        String value = dom.getValue();
        if (value == null) {
            for (Xpp3Dom child : dom.getChildren()) {
                traverseDom(request, project, child);
            }
            return;
        }

        int index = value.indexOf(PLACEHOLDER_START);
        while (index > -1) {
            int end = value.indexOf(PLACEHOLDER_END, index);
            if (end < 0) {
                break;
            }
            String dependency = value.substring(index + PLACEHOLDER_START.length(), end);
            String paths = resolveDependencies(request, project, dependency);
            if (paths != null) {
                value = value.substring(0, index) + paths + value.substring(end + 1);
            }
            index = value.indexOf(PLACEHOLDER_START, end);
        }
        dom.setValue(value);
    }

    protected String resolveDependencies(ProjectBuildingRequest request, MavenProject project, String dependency) throws ProjectBuildingException {
        char separator = dependency.charAt(dependency.length() - 1);
        boolean transitive = isPathSeparator(separator);
        if (transitive) {
            dependency = dependency.substring(0, dependency.length() - 1);
        }

        RepositorySystemSession session = request.getRepositorySession();
        Artifact artifact = toArtifact(project, dependency);
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, null));
        collectRequest.setRepositories(RepositoryUtils.toRepos(request.getRemoteRepositories()));
        ArtifactTypeRegistry typeRegistry = RepositoryUtils.newArtifactTypeRegistry(artifactHandlerManager);
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null) {
            List<Dependency> managed = dependencyManagement.getDependencies()
                    .stream()
                    .map(d -> RepositoryUtils.toDependency(d, typeRegistry))
                    .collect(Collectors.toList());
            collectRequest.setManagedDependencies(managed);
        }

        try {
            DependencyRequest depRequest = new DependencyRequest(collectRequest, null);
            DependencyResult result = repositorySystem.resolveDependencies(session, depRequest);
            return extractArtifacts(result, transitive).stream()
                    .map(Artifact::getFile)
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(String.valueOf(separator)));
        } catch (DependencyResolutionException ex) {
            throw new PlaceholderResolutionException(
                    project.getId(),
                    "Failed to resolve dependency " + dependency,
                    ex
            );
        }
    }

    protected boolean isPathSeparator(char separator) {
        return ',' == separator || ';' == separator || ':' == separator;
    }

    protected boolean isPlaceholderResolutionEnabled(ProjectBuildingRequest request, ProjectBuildingResult result) {
        Properties properties = request.getUserProperties();
        if ("true".equals(properties.get(PLACEHOLDER_RESOLUTION_ENABLED_FLAG))) {
            return true;
        }

        MavenProject project = result.getProject();
        if (project == null) {
            return false;
        }

        properties = project.getProperties();
        return "true".equals(properties.get(PLACEHOLDER_RESOLUTION_ENABLED_FLAG));
    }

    protected Artifact toArtifact(MavenProject project, String dependency) throws ProjectBuildingException {
        // groupId:artifactId[:version[:packaging[:classifier]]]
        boolean hasVersion = false;
        String[] tokens = StringUtils.split(dependency, ":");
        if (tokens.length < 2) {
            throw new PlaceholderResolutionException(
                    project.getId(),
                    "Invalid dependency specified: " + dependency,
                    null
            );
        }
        String groupId = tokens[0];
        String artifactId = tokens[1];
        String version = null;
        if (tokens.length >= 3) {
            hasVersion = true;
            version = tokens[2];
        }
        String packaging = "jar";
        if (tokens.length >= 4) {
            packaging = tokens[3];
        }
        String classifier = null;
        if (tokens.length >= 5) {
            classifier = tokens[4];
        }

        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(packaging);
        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, artifactHandler.getExtension(), version);
        if (!hasVersion) {
            String key = ArtifactIdUtils.toVersionlessId(artifact);
            artifact = Optional.ofNullable(project.getManagedVersionMap().get(key))
                    .map(RepositoryUtils::toArtifact)
                    .orElseThrow(() -> new PlaceholderResolutionException(
                            project.getId(),
                            "No version was specified for artifact " + dependency,
                            null
                    ));
        }

        if (workspaceReader.isReactorArtifact(artifact)) {
            throw new PlaceholderResolutionException(
                    project.getId(),
                    "Artifact " + dependency + " is reactor artifact, IMH extension does not support such configurations",
                    null
            );
        }

        return artifact;
    }

    protected List<Artifact> extractArtifacts(DependencyResult dependencyResult, boolean transitive) {
        List<Artifact> artifacts = new ArrayList<>();
        DependencyNode dependencyNode = dependencyResult.getRoot();
        Artifact rootArtifact = dependencyResult.getRoot().getArtifact();
        artifacts.add(rootArtifact);
        if (!transitive) {
            return artifacts;
        }
        Set<String> seen = new HashSet<>();
        CollectAllDependenciesVisitor visitor = new CollectAllDependenciesVisitor();
        dependencyNode.accept(visitor);
        for (Artifact artifact : visitor.getArtifacts()) {
            if (seen.add(ArtifactIdUtils.toId(artifact))) {
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    static class CollectAllDependenciesVisitor implements DependencyVisitor {

        private boolean root = true;

        private final Set<Artifact> artifacts = new HashSet<>();

        @Override
        public boolean visitEnter(DependencyNode node) {
            if (root) {
                root = false;
                return true;
            }
            return artifacts.add(node.getArtifact());
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            return true;
        }

        public Set<Artifact> getArtifacts() {
            return artifacts;
        }
    }

    static class PlaceholderResolutionException extends ProjectBuildingException {

        public PlaceholderResolutionException(String projectId, String message, Throwable cause) {
            super(projectId, message, cause);
        }

        @Override
        public List<ProjectBuildingResult> getResults() {
            return Collections.emptyList();
        }

    }

}
