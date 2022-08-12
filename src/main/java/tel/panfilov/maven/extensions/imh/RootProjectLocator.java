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

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

@Component(role = RootProjectLocator.class)
public class RootProjectLocator {

    public MavenProject getRootProject(MavenProject project, File rootDirectory) {
        MavenProject root = lookupByMultiModuleProjectDirectory(project, rootDirectory);
        if (root == null) {
            root = lookupRootByFolder(project);
        }
        if (root == null) {
            root = lookupRootByExecution(project);
        }
        if (root == null) {
            root = project;
        }
        return root;
    }

    protected MavenProject lookupByMultiModuleProjectDirectory(MavenProject project, File rootDirectory) {
        if (rootDirectory == null) {
            return null;
        }
        MavenProject root = project;
        while (root != null && !rootDirectory.equals(root.getBasedir())) {
            root = root.getParent();
        }
        return root;
    }

    protected MavenProject lookupRootByFolder(MavenProject project) {
        Predicate<MavenProject> hasMvnFolder = prj -> Optional.of(prj)
                .map(MavenProject::getBasedir)
                .map(dir -> new File(dir, ".mvn"))
                .map(File::isDirectory)
                .orElse(false);
        MavenProject root = project;
        while (root != null && !hasMvnFolder.test(root)) {
            root = root.getParent();
        }
        return root;
    }

    protected MavenProject lookupRootByExecution(MavenProject project) {
        MavenProject root = project;
        while (root != null && !root.isExecutionRoot()) {
            root = root.getParent();
        }
        return root;
    }

}
