package com.tyron.builder.api.internal.initialization;

import com.google.common.hash.HashCode;
import com.tyron.builder.internal.classpath.ClassPath;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Represents a particular node in the ClassLoader graph.
 *
 * Certain domain objects (e.g. Gradle, Settings, Project) have an associated class loader scope. This is used for evaluating associated scripts and script plugins.
 *
 * Use of this class allows class loader creation to be lazy, and potentially optimised. It also provides a central location for class loader reuse.
 */
public interface ClassLoaderScope {
    ClassLoaderScopeId getId();

    /**
     * The classloader for use at this node.
     * <p>
     * Contains exported classes of the parent scope and all local and exported additions to this scope.
     * It is strongly preferable to only call this after {@link #lock() locking} the scope as it allows the structure to be optimized.
     */
    ClassLoader getLocalClassLoader();

    /**
     * The classloader for use by child nodes.
     * <p>
     * Contains exported classes of the parent scope and all local and exported additions to this scope.
     * It is strongly preferable to only call this after {@link #lock() locking} the scope as it allows the structure to be optimized.
     */
    ClassLoader getExportClassLoader();

    /**
     * The parent of this scope.
     */
    ClassLoaderScope getParent();

    /**
     * Returns true if this scope defines the given Class. That is, the class is local and/or exported by this scope and not inherited from
     * some parent.
     */
    boolean defines(Class<?> clazz);

    /**
     * Makes the provided classes visible to this scope, but not to children. The classes are loaded in their own ClassLoader whose parent is the export
     * ClassLoader of the parent scope.
     *
     * <p>Can not be called after being locked.
     *
     * @return this
     */
    ClassLoaderScope local(ClassPath classPath);

    /**
     * Makes the provided classes visible to this scope and its children. The classes are loaded in their own ClassLoader whose parent is the export ClassLoader
     * of the parent scope.
     *
     * <p>Can not be called after being locked.
     *
     * @return this
     */
    ClassLoaderScope export(ClassPath classPath);

    /**
     * Makes the provided classes visible to this scope and its children. The classes are loaded in their own ClassLoader whose parent is the export ClassLoader
     * of the parent scope.
     *
     * <p>Can not be called after being locked.
     *
     * @return this
     */
    ClassLoaderScope export(ClassLoader classLoader);

    /**
     * Creates a scope with this scope as parent.
     *
     * @param id an identifier for the child loader
     */
    ClassLoaderScope createChild(String id);

    /**
     * Creates a child scope that is immutable and ready to use. Uses the given factory to create the local ClassLoader if not already cached. The factory takes a parent ClassLoader and produces a ClassLoader
     */
    ClassLoaderScope createLockedChild(String id, ClassPath localClasspath, @Nullable HashCode classpathImplementationHash, @Nullable Function<ClassLoader, ClassLoader> localClassLoaderFactory);

    /**
     * Signal that no more modifications are to come, allowing the structure to be optimised if possible.
     *
     * @return this
     */
    ClassLoaderScope lock();

    boolean isLocked();

    /**
     * Notifies this scope that it is about to be reused in a new build invocation, so that the scope can recreate or otherwise prepare its classloaders for this, as certain state may have
     * been discarded to reduce memory pressure.
     */
    void onReuse();
}
