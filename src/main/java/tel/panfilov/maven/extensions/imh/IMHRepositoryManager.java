package tel.panfilov.maven.extensions.imh;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import java.nio.file.Path;

@Component(role = IMHRepositoryManager.class, hint = "imh")
public class IMHRepositoryManager extends AbstractProjectAware implements LocalRepositoryManager {

    private LocalRepositoryManager local;

    private LocalRepositoryManager overlay;

    private Path localDir;

    private Path overlayDir;

    public void setRepositoryManagers(LocalRepositoryManager local, LocalRepositoryManager overlay) {
        this.local = local;
        this.overlay = overlay;
        this.localDir = local.getRepository().getBasedir().toPath();
        this.overlayDir = overlay.getRepository().getBasedir().toPath();
    }

    protected String relativize(String path) {
        return localDir.relativize(overlayDir.resolve(path)).toString();
    }

    @Override
    public LocalRepository getRepository() {
        return local.getRepository();
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        if (isReactorArtifact(artifact)) {
            return relativize(overlay.getPathForLocalArtifact(artifact));
        } else {
            return local.getPathForLocalArtifact(artifact);
        }
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        if (isReactorArtifact(artifact)) {
            return relativize(overlay.getPathForRemoteArtifact(artifact, repository, context));
        } else {
            return local.getPathForRemoteArtifact(artifact, repository, context);
        }
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        if (isReactorArtifact(metadata)) {
            return relativize(overlay.getPathForLocalMetadata(metadata));
        } else {
            return local.getPathForLocalMetadata(metadata);
        }
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        if (isReactorArtifact(metadata)) {
            return relativize(overlay.getPathForRemoteMetadata(metadata, repository, context));
        } else {
            return local.getPathForRemoteMetadata(metadata, repository, context);
        }
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        if (isReactorArtifact(request.getArtifact())) {
            return overlay.find(session, request);
        } else {
            return local.find(session, request);
        }
    }

    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        if (isReactorArtifact(request.getArtifact())) {
            overlay.add(session, request);
        } else {
            local.add(session, request);
        }
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        if (isReactorArtifact(request.getMetadata())) {
            return overlay.find(session, request);
        } else {
            return local.find(session, request);
        }
    }

    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        if (isReactorArtifact(request.getMetadata())) {
            overlay.add(session, request);
        } else {
            local.add(session, request);
        }
    }

}
