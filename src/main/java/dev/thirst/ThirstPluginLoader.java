package dev.thirst;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Supplies Thirst's runtime library dependencies via Paper's library loader.
 *
 * <p>The SQLite JDBC driver (used from Phase 4 for per-chunk water quality) is
 * downloaded to the shared Paper library cache at load time instead of being
 * shaded into the plugin jar. This keeps the artifact small and avoids
 * class-path duplication when several plugins use the same driver.
 */
@NullMarked
public class ThirstPluginLoader implements PluginLoader {

    /** Keep in sync with the {@code compileOnly} coordinate in build.gradle.kts. */
    private static final String SQLITE_JDBC = "org.xerial:sqlite-jdbc:3.53.4.0";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact(SQLITE_JDBC), null));
        classpathBuilder.addLibrary(resolver);
    }
}
