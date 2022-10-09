package tel.panfilov.maven.extensions.imh;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractProjectAware {

    protected final Map<String, MavenProject> projectMap = new HashMap<>();

    public void setRootProject(MavenProject project) {
        projectMap.put(getProjectId(project), project);
        List<MavenProject> collected = project.getCollectedProjects();
        if (collected != null) {
            collected.forEach(this::addProject);
        }
    }

    public void addProject(MavenProject project) {
        projectMap.put(getProjectId(project), project);
    }

    public boolean isReactorArtifact(Artifact artifact) {
        return projectMap.containsKey(getProjectId(artifact));
    }

    protected boolean isReactorArtifact(Metadata metadata) {
        return projectMap.containsKey(getProjectId(metadata));
    }


    protected String getProjectId(MavenProject project) {
        return project.getGroupId() + ':' + project.getArtifactId() + ':' + project.getVersion();
    }

    protected String getProjectId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();
    }

    protected String getProjectId(Metadata metadata) {
        return metadata.getGroupId() + ':' + metadata.getArtifactId() + ':' + metadata.getVersion();
    }

    protected String getArtifactId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getExtension() + ':' + artifact.getClassifier();
    }

}
