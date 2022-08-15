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

    protected MavenProject getRootProject(MavenSession mavenSession) throws ProjectBuildingException, ComponentLookupException {
        if (rootProject == null) {
            rootProject = rootProjectLocator.getRootProject(mavenSession);
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


}
