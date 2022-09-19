/*-
 * #%L
 * Maven inter-module helper
 * %%
 * Copyright (C) 2022 Project Contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package tel.panfilov.maven.extensions.imh;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectModelResolver;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Component(role = RootProjectLocator.class)
public class RootProjectLocator {

    @Requirement
    private ProjectBuildingHelper projectBuildingHelper;

    @Requirement
    private MavenRepositorySystem repositorySystem;

    @Requirement
    private org.eclipse.aether.RepositorySystem repoSystem;

    @Requirement
    private RemoteRepositoryManager repositoryManager;

    @Requirement
    private ModelBuilder modelBuilder;

    @Requirement
    private PlexusContainer container;


    public MavenProject getRootProject(MavenSession mavenSession) throws ProjectBuildingException, ComponentLookupException {
        MavenExecutionRequest executionRequest = mavenSession.getRequest();
        RepositorySystemSession repositorySystemSession = tempRepositorySession(mavenSession);
        ProjectBuildingRequest projectBuildingRequest = projectBuildingRequest(executionRequest, repositorySystemSession);
        File rootPom = getRootPom(mavenSession);
        if (rootPom == null) {
            return null;
        }
        ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class, "imh");
        List<File> poms = Collections.singletonList(rootPom);
        for (ProjectBuildingResult projectBuildingResult : projectBuilder.build(poms, true, projectBuildingRequest)) {
            MavenProject project = projectBuildingResult.getProject();
            if (project == null) {
                continue;
            }
            if (rootPom.equals(project.getFile())) {
                return project;
            }
        }
        return null;
    }

    protected File getRootPom(MavenSession mavenSession) {
        MavenExecutionRequest executionRequest = mavenSession.getRequest();
        File rootDirectory = executionRequest.getMultiModuleProjectDirectory();
        RepositorySystemSession repositorySystemSession = tempRepositorySession(mavenSession);
        ProjectBuildingRequest projectBuildingRequest = projectBuildingRequest(executionRequest, repositorySystemSession);
        ModelBuildingRequest modelBuildingRequest = modelBuildingRequest(executionRequest.getPom(), projectBuildingRequest);
        ModelBuildingResult result;
        try {
            result = modelBuilder.build(modelBuildingRequest);
        } catch (ModelBuildingException ex) {
            result = ex.getResult();
        }
        if (result == null) {
            return null;
        }
        return getRootPom(result, rootDirectory);
    }

    protected File getRootPom(ModelBuildingResult result, File rootDirectory) {
        Predicate<File> isRoot = dir -> dir.equals(rootDirectory);
        Predicate<File> hasMvnFolder = dir -> new File(dir, ".mvn").isDirectory();
        Predicate<File> hasPom = dir -> new File(dir, "pom.xml").isFile();
        Predicate<File> rootFolder = isRoot.or(hasMvnFolder);
        File rootPom = null;
        for (String modelId : result.getModelIds()) {
            Model model = result.getRawModel(modelId);
            File pom = model.getPomFile();
            if (pom == null) {
                break;
            }
            if (rootFolder.test(pom.getParentFile())) {
                rootPom = pom;
                break;
            }
        }
        if (rootPom == null) {
            if (hasPom.and(hasMvnFolder).test(rootDirectory)) {
                rootPom = new File(rootDirectory, "pom.xml");
            }
        }
        return rootPom;
    }

    protected ProjectBuildingRequest projectBuildingRequest(MavenExecutionRequest request, RepositorySystemSession systemSession) {
        ProjectBuildingRequest buildingRequest = request.getProjectBuildingRequest();
        buildingRequest = new DefaultProjectBuildingRequest(buildingRequest);
        buildingRequest.setRepositorySession(systemSession);
        buildingRequest.setResolveDependencies(false);
        buildingRequest.setProcessPlugins(true);
        buildingRequest.setResolveVersionRanges(false);
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return buildingRequest;
    }

    protected RepositorySystemSession tempRepositorySession(MavenSession session) {
        DefaultRepositorySystemSession result = new DefaultRepositorySystemSession(session.getRepositorySession());
        result.setWorkspaceReader(null);
        result.setArtifactDescriptorPolicy((s, r) -> ArtifactDescriptorPolicy.IGNORE_ERRORS);
        result.setCache(new DefaultRepositoryCache());
        return result;
    }

    protected ModelBuildingRequest modelBuildingRequest(File pom, ProjectBuildingRequest projectBuildingRequest) {
        ModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setPomFile(pom);
        request.setValidationLevel(projectBuildingRequest.getValidationLevel());
        request.setProcessPlugins(projectBuildingRequest.isProcessPlugins());
        request.setProfiles(projectBuildingRequest.getProfiles());
        request.setActiveProfileIds(projectBuildingRequest.getActiveProfileIds());
        request.setInactiveProfileIds(projectBuildingRequest.getInactiveProfileIds());
        request.setSystemProperties(projectBuildingRequest.getSystemProperties());
        request.setUserProperties(projectBuildingRequest.getUserProperties());
        request.setBuildStartTime(projectBuildingRequest.getBuildStartTime());
        request.setModelResolver(modelResolver(projectBuildingRequest));
        request.setModelCache(new ReactorModelCache());
        return request;
    }

    protected ModelResolver modelResolver(ProjectBuildingRequest projectBuildingRequest) {
        ModelBuildingRequest request = new DefaultModelBuildingRequest();
        RequestTrace trace = RequestTrace.newChild(null, projectBuildingRequest).newChild(request);
        List<RemoteRepository> repositories = RepositoryUtils.toRepos(projectBuildingRequest.getRemoteRepositories());
        return new ProjectModelResolver(
                projectBuildingRequest.getRepositorySession(),
                trace,
                repoSystem,
                repositoryManager,
                repositories,
                projectBuildingRequest.getRepositoryMerging(),
                null
        );
    }

}
