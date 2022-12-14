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
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

@Component(role = IMHWorkspaceReader.class, hint = "imh")
public class IMHWorkspaceReader extends AbstractProjectAware implements WorkspaceReader {

    public static final String DIRECTORY_FALLBACK_ENABLED_FLAG = "imh.directoryfallback";

    private final WorkspaceRepository repository = new WorkspaceRepository();

    @Requirement
    private Logger logger;

    private MavenExecutionRequest mavenExecutionRequest;

    public void setMavenExecutionRequest(MavenExecutionRequest mavenExecutionRequest) {
        this.mavenExecutionRequest = mavenExecutionRequest;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        if ("pom".equals(artifact.getExtension())) {
            return getPom(artifact);
        }
        return getArtifact(artifact);
    }

    protected File getPom(Artifact artifact) {
        return Optional.of(getProjectId(artifact))
                .map(projectMap::get)
                .map(MavenProject::getFile)
                .filter(File::exists)
                .orElse(null);
    }

    protected File getArtifact(Artifact artifact) {
        MavenProject project = projectMap.get(getProjectId(artifact));
        if (project == null) {
            return null;
        }

        File file = findProjectArtifact(project, artifact);
        if (file != null) {
            return file;
        }

        Build build = project.getBuild();
        // javadoc promises it is  ${artifactId}-${version}
        StringBuilder name = new StringBuilder(build.getFinalName());
        if (!StringUtils.isEmpty(artifact.getClassifier())) {
            name.append('-').append(artifact.getClassifier());
        }
        name.append('.').append(artifact.getExtension());
        file = new File(build.getDirectory(), name.toString());
        if (isActual(file, artifact, project)) {
            return file;
        }

        if (isDirectoryFallbackEnabled(project)) {
            Path directory;
            if (isTestArtifact(artifact)) {
                directory = Paths.get(build.getTestOutputDirectory());
            } else {
                directory = Paths.get(build.getOutputDirectory());
            }

            return directory.toFile();
        }

        return null;
    }

    protected boolean isArtifactFile(File file) {
        return file != null && file.exists() && file.isFile();
    }

    protected boolean isActual(File packaged, Artifact artifact, MavenProject project) {
        if (!isArtifactFile(packaged)) {
            return false;
        }

        Build build = project.getBuild();
        Path directory;
        if (isTestArtifact(artifact)) {
            directory = Paths.get(build.getTestOutputDirectory());
        } else {
            directory = Paths.get(build.getOutputDirectory());
        }

        if (Files.notExists(directory) || !Files.isDirectory(directory)) {
            return true;
        }

        long buildStartTime = Optional.ofNullable(mavenExecutionRequest)
                .map(MavenExecutionRequest::getProjectBuildingRequest)
                .map(ProjectBuildingRequest::getBuildStartTime)
                .map(Date::getTime)
                .orElse(-1L);

        try (Stream<Path> outputFiles = Files.walk(directory)) {
            long artifactTime = Files.getLastModifiedTime(packaged.toPath()).toMillis();
            if (buildStartTime > 0 && artifactTime > buildStartTime) {
                return true;
            }

            Iterator<Path> iterator = outputFiles.iterator();
            while (iterator.hasNext()) {
                Path outputFile = iterator.next();

                if (Files.isDirectory(outputFile)) {
                    continue;
                }

                long outputFileLastModified = Files.getLastModifiedTime(outputFile).toMillis();
                if (outputFileLastModified > artifactTime) {
                    logger.debug("[IMH] File '" + packaged + "' seems to be stale, found newer file in build directory: " + outputFile);
                    return false;
                }
            }

            return true;
        } catch (IOException e) {
            logger.warn("[IMH] Failed to check whether the packaged artifact is up-to-date, assuming it is", e);
            return true;
        }
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        MavenProject project = projectMap.get(getProjectId(artifact));
        if (project != null) {
            return Collections.singletonList(project.getVersion());
        }
        return Collections.emptyList();
    }

    protected File findProjectArtifact(MavenProject project, Artifact requested) {
        String requestedId = getArtifactId(requested);
        return Stream.concat(Stream.of(project.getArtifact()), project.getAttachedArtifacts().stream())
                .filter(Objects::nonNull)
                .map(RepositoryUtils::toArtifact)
                .filter(a -> requestedId.equals(getArtifactId(a)))
                .filter(a -> Objects.equals(requested.getVersion(), a.getVersion()))
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .filter(File::exists)
                .findFirst()
                .orElse(null);
    }

    protected boolean isTestArtifact(Artifact artifact) {
        return ("test-jar".equals(artifact.getProperty("type", "")))
                || ("jar".equals(artifact.getExtension()) && "tests".equals(artifact.getClassifier()));
    }

    protected boolean isDirectoryFallbackEnabled(MavenProject project) {
        Properties properties = project.getProperties();
        if ("true".equalsIgnoreCase(properties.getProperty(DIRECTORY_FALLBACK_ENABLED_FLAG))) {
            return true;
        }

        if (mavenExecutionRequest == null) {
            return false;
        }

        properties = mavenExecutionRequest.getUserProperties();
        return "true".equalsIgnoreCase(properties.getProperty(DIRECTORY_FALLBACK_ENABLED_FLAG));
    }


}
