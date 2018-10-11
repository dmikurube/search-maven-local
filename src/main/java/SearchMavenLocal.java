/*
 * Copyright 2018 Dai MIKURUBE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.IOError;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public class SearchMavenLocal {
    public static void main(final String[] args) {
    }
}

class MavenArtifactFinder {
    private MavenArtifactFinder(final Path givenLocalMavenRepositoryPath,
                                final Path absoluteLocalMavenRepositoryPath,
                                final RepositorySystem repositorySystem,
                                final RepositorySystemSession repositorySystemSession) {
        this.givenLocalMavenRepositoryPath = givenLocalMavenRepositoryPath;
        this.absoluteLocalMavenRepositoryPath = absoluteLocalMavenRepositoryPath;
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
    }

    public static MavenArtifactFinder create(final Path localMavenRepositoryPath)
            throws MavenRepositoryNotFoundException {
        final Path absolutePath;
        try {
            absolutePath = localMavenRepositoryPath.normalize().toAbsolutePath();
        } catch (IOError ex) {
            throw new MavenRepositoryNotFoundException(localMavenRepositoryPath, ex);
        } catch (SecurityException ex) {
            throw new MavenRepositoryNotFoundException(localMavenRepositoryPath, ex);
        }

        if (!Files.exists(absolutePath)) {
            throw new MavenRepositoryNotFoundException(localMavenRepositoryPath,
                                                       absolutePath,
                                                       new NoSuchFileException(absolutePath.toString()));
        }
        if (!Files.isDirectory(absolutePath)) {
            throw new MavenRepositoryNotFoundException(localMavenRepositoryPath,
                                                       absolutePath,
                                                       new NotDirectoryException(absolutePath.toString()));
        }

        final RepositorySystem repositorySystem = createRepositorySystem();

        return new MavenArtifactFinder(localMavenRepositoryPath,
                                       absolutePath,
                                       repositorySystem,
                                       createRepositorySystemSession(repositorySystem, absolutePath));
    }

    /**
     * Finds a Maven-based plugin JAR with its "direct" dependencies.
     *
     * @see <a href="https://github.com/eclipse/aether-demo/blob/322fa556494335faaf3ad3b7dbe8f89aaaf6222d/aether-demo-snippets/src/main/java/org/eclipse/aether/examples/GetDirectDependencies.java">aether-demo's GetDirectDependencies.java</a>
     */
    public final MavenPluginPaths findMavenPluginJarsWithDirectDependencies(
            final String groupId,
            final String artifactId,
            final String classifier,
            final String version)
            throws MavenArtifactNotFoundException {
        final ArtifactDescriptorResult result;
        try {
            result = this.describeMavenArtifact(groupId, artifactId, classifier, "jar", version);
        } catch (ArtifactDescriptorException ex) {
            throw new MavenArtifactNotFoundException(groupId, artifactId, classifier, version,
                                                     this.givenLocalMavenRepositoryPath,
                                                     this.absoluteLocalMavenRepositoryPath,
                                                     ex);
        }
        final ArrayList<Path> dependencyPaths = new ArrayList<>();
        for (final Dependency dependency : result.getDependencies()) {
            final Path dependencyPath = this.findMavenArtifact(dependency.getArtifact());
            dependencyPaths.add(dependencyPath);
        }
        final Path artifactPath = this.findMavenArtifact(result.getArtifact());
        return MavenPluginPaths.of(artifactPath, dependencyPaths);
    }

    public Path findMavenArtifact(
            final Artifact artifact) throws MavenArtifactNotFoundException {
        final ArtifactResult result;
        try {
            result = this.repositorySystem.resolveArtifact(
                    this.repositorySystemSession, new ArtifactRequest().setArtifact(artifact));
        } catch (ArtifactResolutionException ex) {
            throw new MavenArtifactNotFoundException(artifact,
                                                     this.givenLocalMavenRepositoryPath,
                                                     this.absoluteLocalMavenRepositoryPath,
                                                     ex);
        }
        return result.getArtifact().getFile().toPath();
    }

    private ArtifactDescriptorResult describeMavenArtifact(
            final String groupId,
            final String artifactId,
            final String classifier,
            final String extension,
            final String version)
            throws ArtifactDescriptorException {
        // |classifier| can be null for |org.eclipse.aether.artifact.DefaultArtifact|.
        final ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest()
                .setArtifact(new DefaultArtifact(groupId, artifactId, classifier, extension, version));

        return this.repositorySystem.readArtifactDescriptor(this.repositorySystemSession, descriptorRequest);
    }

    private static RepositorySystem createRepositorySystem() {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession createRepositorySystemSession(
            final RepositorySystem repositorySystem, final Path localRepositoryPath) {
        final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        final LocalRepository repository = new LocalRepository(localRepositoryPath.toString());
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, repository));
        return session;
    }

    // Paths are kept just for hinting in Exceptions.
    private final Path givenLocalMavenRepositoryPath;
    private final Path absoluteLocalMavenRepositoryPath;

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
}

class MavenPluginPaths {
    private MavenPluginPaths(final Path pluginJarPath, final List<Path> pluginDependencyJarPaths) {
        this.pluginJarPath = pluginJarPath;
        if (pluginDependencyJarPaths == null) {
            this.pluginDependencyJarPaths = Collections.emptyList();
        } else {
            this.pluginDependencyJarPaths = Collections.unmodifiableList(pluginDependencyJarPaths);
        }
    }

    public static MavenPluginPaths of(final Path pluginJarPath) {
        return new MavenPluginPaths(pluginJarPath, null);
    }

    public static MavenPluginPaths of(final Path pluginJarPath, final Path... pluginDependencyJarPaths) {
        return new MavenPluginPaths(pluginJarPath, Arrays.asList(pluginDependencyJarPaths));
    }

    public static MavenPluginPaths of(final Path pluginJarPath, final List<Path> pluginDependencyJarPaths) {
        return new MavenPluginPaths(pluginJarPath, pluginDependencyJarPaths);
    }

    public Path getPluginJarPath() {
        return this.pluginJarPath;
    }

    public List<Path> getPluginDependencyJarPaths() {
        return this.pluginDependencyJarPaths;
    }

    private final Path pluginJarPath;
    private final List<Path> pluginDependencyJarPaths;
}

class MavenArtifactNotFoundException extends Exception {
    public MavenArtifactNotFoundException(final String groupId,
                                          final String artifactId,
                                          final String classifier,
                                          final String version,
                                          final Path givenRepositoryPath,
                                          final Path absoluteRepositoryPath,
                                          final Throwable cause) {
        super("Maven artifact \"" + groupId + ":" + artifactId + ":" + version
                      + (classifier != null ? (":" + classifier) : "") + "\" is not found: at \""
                      + givenRepositoryPath.toString() + "\" (\"" + absoluteRepositoryPath.toString() + "\").",
              cause);
    }

    public MavenArtifactNotFoundException(final Artifact artifact,
                                          final Path givenRepositoryPath,
                                          final Path absoluteRepositoryPath,
                                          final Throwable cause) {
        super("Maven artifact \"" + artifact.toString() + "\" is not found: at \""
                      + givenRepositoryPath.toString() + "\" (\"" + absoluteRepositoryPath.toString() + "\").",
              cause);
    }
}

class MavenRepositoryNotFoundException extends Exception {
    public MavenRepositoryNotFoundException(final Path givenPath,
                                            final Throwable cause) {
        super("Maven repository specified is not found at \"" + givenPath.toString() + "\".", cause);
    }

    public MavenRepositoryNotFoundException(final Path givenPath,
                                            final Path absolutePath,
                                            final Throwable cause) {
        super("Maven repository specified is not found at \"" + givenPath.toString()
                      + "\" (\"" + absolutePath.toString() + "\").",
              cause);
    }

    public MavenRepositoryNotFoundException(final String message,
                                            final Path givenPath,
                                            final Path absolutePath,
                                            final Throwable cause) {
        super("Maven repository specified is not found at \"" + givenPath.toString()
                      + "\" (\"" + absolutePath.toString() + "\"): " + message,
              cause);
    }
}
