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
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class IMHLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    public static final String WORKSPACE_ENABLED_LEGACY_FLAG = "imh.ext";

    public static final String WORKSPACE_ENABLED_FLAG = "imh.workspace";

    public static final String REPOSITORY_ENABLED_FLAG = "imh.repository";

    @Requirement
    private PlexusContainer container;

    @Requirement
    private Logger logger;

    @Requirement
    private RootProjectLocator rootProjectLocator;

    @Requirement(hint = "imh")
    private IMHWorkspaceReader workspaceReader;

    @Requirement(hint = "imh")
    private IMHRepositoryManager repositoryManager;

    @Requirement
    private RepositorySystem repoSystem;

    private MavenProject rootProject;

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        setupWorkspaceReader(session);
        setupLocalRepositoryManager(session);
    }

    protected void setupLocalRepositoryManager(MavenSession mavenSession) {
        try {
            if (!isRepositoryEnabled(mavenSession)) {
                logger.info("[IMH] repository extension disabled");
                return;
            }

            logger.info("[IMH] setting up overlay repository");

            MavenProject rootProject = getRootProject(mavenSession);
            File overlayPath = getOverlayRepositoryPath(rootProject);
            if (overlayPath == null) {
                logger.info("[IMH] empty overlay repository path");
                return;
            }

            DefaultRepositorySystemSession repositorySession = (DefaultRepositorySystemSession) mavenSession.getRepositorySession();
            repositoryManager.setRootProject(rootProject);
            repositoryManager.setRepositoryManagers(
                    repositorySession.getLocalRepositoryManager(),
                    repoSystem.newLocalRepositoryManager(
                            repositorySession,
                            new LocalRepository(overlayPath)
                    )
            );
            repositorySession.setLocalRepositoryManager(repositoryManager);

        } catch (ComponentLookupException | ProjectBuildingException ex) {
            logger.error("[IMH] Failed to setup repository", ex);
        }
    }

    protected File getOverlayRepositoryPath(MavenProject rootProject) {
        String path = rootProject.getProperties().getProperty("imh.repository");
        if (!StringUtils.isEmpty(path)) {
            logger.info("[IMH] using overlay repository from root project properties: " + path);
            return new File(path);
        }
        File repository = new File(rootProject.getBuild().getDirectory(), "local-repo");
        logger.info("[IMH] using root project target folder as overlay repository: " + repository.getPath());
        return repository;
    }

    protected void setupWorkspaceReader(MavenSession mavenSession) {
        try {
            if (!isWorkspaceEnabled(mavenSession)) {
                logger.info("[IMH] workspace extension disabled");
                return;
            }

            MavenExecutionRequest request = mavenSession.getRequest();
            if (request.getPom() == null || !request.getPom().isFile()) {
                logger.info("[IMH] Pom file not found");
                return;
            }

            logger.debug("[IMH] setting up workspace");
            workspaceReader.setRootProject(getRootProject(mavenSession));

        } catch (ComponentLookupException | ProjectBuildingException ex) {
            logger.error("[IMH] Failed to setup workspace reader", ex);
        }
    }

    protected MavenProject getRootProject(MavenSession mavenSession) throws ProjectBuildingException, ComponentLookupException {
        if (rootProject == null) {
            MavenExecutionRequest request = mavenSession.getRequest();
            RepositorySystemSession repositorySystemSession = tempRepositorySession(mavenSession);
            MavenProject project = getProject(request, repositorySystemSession);
            project = rootProjectLocator.getRootProject(project, request.getMultiModuleProjectDirectory());
            rootProject = populateRootProject(project, request, repositorySystemSession);
        }
        return rootProject;
    }

    protected MavenProject getProject(MavenExecutionRequest request, RepositorySystemSession repositorySystemSession) throws ProjectBuildingException, ComponentLookupException {
        ProjectBuildingRequest buildingRequest = projectBuildingRequest(request, repositorySystemSession);
        ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class, "imh");
        return projectBuilder.build(request.getPom(), buildingRequest).getProject();
    }

    protected MavenProject populateRootProject(MavenProject rootProject, MavenExecutionRequest request, RepositorySystemSession repositorySystemSession) throws ProjectBuildingException, ComponentLookupException {
        List<File> poms = Collections.singletonList(rootProject.getFile());
        ProjectBuildingRequest buildingRequest = projectBuildingRequest(request, repositorySystemSession);
        ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class, "imh");
        for (ProjectBuildingResult result : projectBuilder.build(poms, true, buildingRequest)) {
            MavenProject project = result.getProject();
            if (project == null) {
                continue;
            }
            if (rootProject.getFile().equals(project.getFile())) {
                return project;
            }
        }
        return rootProject;
    }

    protected boolean isWorkspaceEnabled(MavenSession session) {
        return "true".equals(session.getUserProperties().get(WORKSPACE_ENABLED_LEGACY_FLAG))
                || "true".equals(session.getUserProperties().get(WORKSPACE_ENABLED_FLAG));
    }

    protected boolean isRepositoryEnabled(MavenSession session) {
        return "true".equals(session.getUserProperties().get(REPOSITORY_ENABLED_FLAG));
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
