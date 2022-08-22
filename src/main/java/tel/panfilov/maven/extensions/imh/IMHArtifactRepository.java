package tel.panfilov.maven.extensions.imh;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.repository.Proxy;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class IMHArtifactRepository implements ArtifactRepository {

    private final ArtifactRepository delegate;

    private IMHWorkspaceReader workspaceReader;

    private IMHRepositoryManager repositoryManager;

    public IMHArtifactRepository(ArtifactRepository delegate) {
        this.delegate = delegate;
    }

    public void setWorkspaceReader(IMHWorkspaceReader workspaceReader) {
        this.workspaceReader = workspaceReader;
    }

    public void setRepositoryManager(IMHRepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    protected boolean isArtifactFile(File file) {
        return file != null && file.exists() && file.isFile();
    }

    @Override
    public String pathOf(Artifact artifact) {
        File file = null;
        if (workspaceReader != null) {
            file = workspaceReader.findArtifact(RepositoryUtils.toArtifact(artifact));
        }

        if (!(isArtifactFile(file)) && repositoryManager != null) {
            file = repositoryManager.getLocalArtifact(RepositoryUtils.toArtifact(artifact));
        }

        if (isArtifactFile(file)) {
            Path baseDir = new File(delegate.getBasedir()).toPath();
            return baseDir.relativize(file.toPath()).toString();
        }

        return delegate.pathOf(artifact);
    }

    @Override
    public String pathOfRemoteRepositoryMetadata(ArtifactMetadata artifactMetadata) {
        return delegate.pathOfRemoteRepositoryMetadata(artifactMetadata);
    }

    @Override
    public String pathOfLocalRepositoryMetadata(ArtifactMetadata metadata, ArtifactRepository repository) {
        return delegate.pathOfLocalRepositoryMetadata(metadata, repository);
    }

    @Override
    public String getUrl() {
        return delegate.getUrl();
    }

    @Override
    public void setUrl(String url) {
        delegate.setUrl(url);
    }

    @Override
    public String getBasedir() {
        return delegate.getBasedir();
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public void setId(String id) {
        delegate.setId(id);
    }

    @Override
    public ArtifactRepositoryPolicy getSnapshots() {
        return delegate.getSnapshots();
    }

    @Override
    public void setSnapshotUpdatePolicy(ArtifactRepositoryPolicy policy) {
        delegate.setSnapshotUpdatePolicy(policy);
    }

    @Override
    public ArtifactRepositoryPolicy getReleases() {
        return delegate.getReleases();
    }

    @Override
    public void setReleaseUpdatePolicy(ArtifactRepositoryPolicy policy) {
        delegate.setReleaseUpdatePolicy(policy);
    }

    @Override
    public ArtifactRepositoryLayout getLayout() {
        return delegate.getLayout();
    }

    @Override
    public void setLayout(ArtifactRepositoryLayout layout) {
        delegate.setLayout(layout);
    }

    @Override
    public String getKey() {
        return delegate.getKey();
    }

    @Override
    @Deprecated
    public boolean isUniqueVersion() {
        return delegate.isUniqueVersion();
    }

    @Override
    @Deprecated
    public boolean isBlacklisted() {
        return delegate.isBlacklisted();
    }

    @Override
    @Deprecated
    public void setBlacklisted(boolean blackListed) {
        delegate.setBlacklisted(blackListed);
    }

    @Override
    public Artifact find(Artifact artifact) {
        if (workspaceReader != null) {
            File file = workspaceReader.findArtifact(RepositoryUtils.toArtifact(artifact));
            if (isArtifactFile(file)) {
                artifact.setFile(file);
                return artifact;
            }
        }
        if (repositoryManager != null) {
            File file = repositoryManager.getLocalArtifact(RepositoryUtils.toArtifact(artifact));
            if (isArtifactFile(file)) {
                artifact.setFile(file);
                return artifact;
            }
        }
        return delegate.find(artifact);
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return delegate.findVersions(artifact);
    }

    @Override
    public boolean isProjectAware() {
        return true;
    }

    @Override
    public Authentication getAuthentication() {
        return delegate.getAuthentication();
    }

    @Override
    public void setAuthentication(Authentication authentication) {
        delegate.setAuthentication(authentication);
    }

    @Override
    public Proxy getProxy() {
        return delegate.getProxy();
    }

    @Override
    public void setProxy(Proxy proxy) {
        delegate.setProxy(proxy);
    }

    @Override
    public List<ArtifactRepository> getMirroredRepositories() {
        return delegate.getMirroredRepositories();
    }

    @Override
    public void setMirroredRepositories(List<ArtifactRepository> mirroredRepositories) {
        delegate.setMirroredRepositories(mirroredRepositories);
    }

}
