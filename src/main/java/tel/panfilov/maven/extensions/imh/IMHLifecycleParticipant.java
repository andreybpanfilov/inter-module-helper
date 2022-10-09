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
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.util.repository.ChainedWorkspaceReader;

import java.io.File;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class IMHLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    public static final String WORKSPACE_ENABLED_LEGACY_FLAG = "imh.ext";

    public static final String WORKSPACE_ENABLED_FLAG = "imh.workspace";

    public static final String REPOSITORY_ENABLED_FLAG = "imh.repository";

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
        setupLocalRepository(session);
    }

    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        for (MavenProject project : session.getProjects()) {
            if (isWorkspaceEnabled(session)) {
                workspaceReader.addProject(project);
            }
            if (isRepositoryEnabled(session)) {
                repositoryManager.addProject(project);
            }
        }
    }

    protected void setupLocalRepository(MavenSession mavenSession) {
        if (isWorkspaceEnabled(mavenSession) || isRepositoryEnabled(mavenSession)) {
            MavenExecutionRequest request = mavenSession.getRequest();
            IMHArtifactRepository localRepository = new IMHArtifactRepository(request.getLocalRepository());
            if (isWorkspaceEnabled(mavenSession)) {
                localRepository.setWorkspaceReader(workspaceReader);
            }
            if (isRepositoryEnabled(mavenSession)) {
                localRepository.setRepositoryManager(repositoryManager);
            }
            request.setLocalRepository(localRepository);
        }
    }

    protected void setupLocalRepositoryManager(MavenSession mavenSession) {
        try {
            if (!isRepositoryEnabled(mavenSession)) {
                logger.info("[IMH] repository extension disabled");
                return;
            }

            logger.info("[IMH] setting up overlay repository");

            MavenProject rootProject = getRootProject(mavenSession);
            if (rootProject == null) {
                logger.info("[IMH] failed to discover root project");
                return;
            }

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
            MavenExecutionRequest request = mavenSession.getRequest();
            if (request.getPom() == null || !request.getPom().isFile()) {
                logger.warn("[IMH] Pom file not found");
                return;
            }

            injectWorkspaceReader(mavenSession, request);

            if (!isWorkspaceEnabled(mavenSession)) {
                logger.info("[IMH] workspace extension disabled");
                return;
            }

            logger.debug("[IMH] setting up workspace");
            MavenProject rootProject = getRootProject(mavenSession);
            if (rootProject == null) {
                logger.info("[IMH] failed to discover root project");
                return;
            }

            workspaceReader.setRootProject(rootProject);
        } catch (ComponentLookupException | ProjectBuildingException ex) {
            logger.error("[IMH] Failed to setup workspace reader", ex);
        }
    }

    protected void injectWorkspaceReader(MavenSession mavenSession, MavenExecutionRequest request) {
        // that would be better to use EventSpy#onEvent instead,
        // however IntelliJ triggers afterSessionStart event only

        workspaceReader.setMavenExecutionRequest(request);

        DefaultRepositorySystemSession repositorySystemSession = (DefaultRepositorySystemSession) mavenSession.getRepositorySession();
        repositorySystemSession.setWorkspaceReader(ChainedWorkspaceReader.newInstance(
                workspaceReader,
                repositorySystemSession.getWorkspaceReader()
        ));
    }

    protected MavenProject getRootProject(MavenSession mavenSession) throws ProjectBuildingException, ComponentLookupException {
        if (rootProject == null) {
            rootProject = rootProjectLocator.getRootProject(mavenSession);
        }
        return rootProject;
    }

    protected boolean isWorkspaceEnabled(MavenSession session) {
        return "true".equalsIgnoreCase(session.getUserProperties().getProperty(WORKSPACE_ENABLED_LEGACY_FLAG))
                || "true".equalsIgnoreCase(session.getUserProperties().getProperty(WORKSPACE_ENABLED_FLAG));
    }

    protected boolean isRepositoryEnabled(MavenSession session) {
        return "true".equalsIgnoreCase(session.getUserProperties().getProperty(REPOSITORY_ENABLED_FLAG));
    }


}
