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

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.util.repository.ChainedWorkspaceReader;

import java.util.Date;
import java.util.Optional;

@Component(role = EventSpy.class, hint = "imh")
public class IMHEventSpy extends AbstractEventSpy {

    @Requirement(hint = "imh")
    private IMHWorkspaceReader workspaceReader;

    @Override
    public void onEvent(Object event) {
        if (event instanceof MavenExecutionRequest) {
            MavenExecutionRequest request = (MavenExecutionRequest) event;
            Optional.of(request)
                    .map(MavenExecutionRequest::getProjectBuildingRequest)
                    .map(ProjectBuildingRequest::getBuildStartTime)
                    .map(Date::getTime)
                    .ifPresent(workspaceReader::setBuildStartTime);
            request.setWorkspaceReader(ChainedWorkspaceReader.newInstance(
                    workspaceReader,
                    request.getWorkspaceReader()
            ));
        }
    }

}
