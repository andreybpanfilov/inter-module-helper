package tel.panfilov.maven.extensions.imh;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.sisu.plexus.Hints;

import java.io.File;
import java.util.List;

@Component(role = ProjectBuilder.class, hint = Hints.DEFAULT_HINT)
public class PlaceholderAwareProjectBuilder implements ProjectBuilder {

    @Requirement(hint = "imh")
    protected ProjectBuilder projectBuilder;

    @Requirement(hint = "imh")
    protected IMHPlaceholderResolver placeHolderResolver;

    @Override
    public ProjectBuildingResult build(File projectFile, ProjectBuildingRequest request) throws ProjectBuildingException {
        ProjectBuildingResult result = projectBuilder.build(projectFile, request);
        placeHolderResolver.resolvePlaceholders(request, result);
        return result;
    }

    @Override
    public ProjectBuildingResult build(Artifact projectArtifact, ProjectBuildingRequest request) throws ProjectBuildingException {
        ProjectBuildingResult result = projectBuilder.build(projectArtifact, request);
        placeHolderResolver.resolvePlaceholders(request, result);
        return result;
    }

    @Override
    public ProjectBuildingResult build(Artifact projectArtifact, boolean allowStubModel, ProjectBuildingRequest request) throws ProjectBuildingException {
        ProjectBuildingResult result = projectBuilder.build(projectArtifact, allowStubModel, request);
        placeHolderResolver.resolvePlaceholders(request, result);
        return result;
    }

    @Override
    public ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request) throws ProjectBuildingException {
        ProjectBuildingResult result = projectBuilder.build(modelSource, request);
        placeHolderResolver.resolvePlaceholders(request, result);
        return result;
    }

    @Override
    public List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive, ProjectBuildingRequest request) throws ProjectBuildingException {
        List<ProjectBuildingResult> result = projectBuilder.build(pomFiles, recursive, request);
        for (ProjectBuildingResult r : result) {
            placeHolderResolver.resolvePlaceholders(request, r);
        }
        return result;
    }

}