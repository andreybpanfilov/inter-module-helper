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

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class IMHLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    public static final String ENABLED_FLAG = "imh.ext";

    @Requirement
    private PlexusContainer container;

    @Requirement
    private Logger logger;

    @Requirement
    private RootProjectLocator rootProjectLocator;

    @Requirement
    private IMHWorkspaceReader workspaceReader;

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        logger.debug("Fake repository extension - setting up workspace");
        setupWorkspaceReader(session);
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        for (MavenProject project : session.getProjects()) {
            workspaceReader.addProject(project);
        }
    }

    protected void setupWorkspaceReader(MavenSession mavenSession) {
        try {
            if (!isEnabled(mavenSession)) {
                logger.debug("inter-module helper extension disabled");
                return;
            }

            MavenExecutionRequest request = mavenSession.getRequest();
            RepositorySystemSession repositorySystemSession = tempRepositorySession(mavenSession);
            if (request.getPom() == null || !request.getPom().isFile()) {
                logger.debug("Pom file not found");
                return;
            }

            MavenProject project = getProject(request, repositorySystemSession);
            MavenProject rootProject = rootProjectLocator.getRootProject(project, request.getMultiModuleProjectDirectory());
            List<MavenProject> workspaceProjects = collectWorkspaceProjects(rootProject, request, repositorySystemSession);
            for (MavenProject mavenProject : workspaceProjects) {
                workspaceReader.addProject(mavenProject);
            }

        } catch (ComponentLookupException | ProjectBuildingException ex) {
            logger.error("Failed to setup workspace reader", ex);
        }
    }

    protected MavenProject getProject(MavenExecutionRequest request, RepositorySystemSession repositorySystemSession) throws ProjectBuildingException, ComponentLookupException {
        ProjectBuildingRequest buildingRequest = projectBuildingRequest(request, repositorySystemSession);
        ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class, "imh");
        return projectBuilder.build(request.getPom(), buildingRequest).getProject();
    }

    protected List<MavenProject> collectWorkspaceProjects(MavenProject rootProject, MavenExecutionRequest request, RepositorySystemSession repositorySystemSession) throws ProjectBuildingException, ComponentLookupException {
        List<File> poms = Collections.singletonList(rootProject.getFile());
        ProjectBuildingRequest buildingRequest = projectBuildingRequest(request, repositorySystemSession);
        ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class, "imh");
        List<ProjectBuildingResult> results = projectBuilder.build(poms, true, buildingRequest);
        List<MavenProject> discovered = new ArrayList<>();
        for (ProjectBuildingResult result : results) {
            discovered.add(result.getProject());
        }
        return discovered;
    }

    protected boolean isEnabled(MavenSession session) {
        return "true".equals(session.getUserProperties().get(ENABLED_FLAG));
    }

    private ProjectBuildingRequest projectBuildingRequest(MavenExecutionRequest request, RepositorySystemSession systemSession) {
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

}
