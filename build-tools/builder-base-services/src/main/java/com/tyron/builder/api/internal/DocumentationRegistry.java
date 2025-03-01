package com.tyron.builder.api.internal;

import org.jetbrains.annotations.NotNull;

public class DocumentationRegistry {

    public static class GradleVersion implements Comparable {

        public static GradleVersion current() {
            return new GradleVersion("0.0.1");
        }

        private final String version;

        public GradleVersion(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        public static GradleVersion version(String version) {
            return new GradleVersion(version);
        }
        @Override
        public int compareTo(@NotNull Object o) {
            return version.compareTo(((GradleVersion) o).version);
        }

        public boolean isSnapshot() {
            return false;
        }

        public GradleVersion getBaseVersion() {
            return current();
        }
    }
    private final GradleVersion gradleVersion;

    public DocumentationRegistry() {
        this.gradleVersion = GradleVersion.current();
    }

    /**
     * Returns the location of the documentation for the given feature, referenced by id. The location may be local or remote.
     */
    public String getDocumentationFor(String id) {
        return String.format("https://docs.gradle.org/%s/userguide/%s.html", gradleVersion.getVersion(), id);
    }

    public String getDocumentationFor(String id, String section) {
        return String.format("https://docs.gradle.org/%s/userguide/%s.html#%s", gradleVersion.getVersion(), id, section);
    }

    public String getDslRefForProperty(Class<?> clazz, String property) {
        String className = clazz.getName();
        return String.format("https://docs.gradle.org/%s/dsl/%s.html#%s:%s", gradleVersion.getVersion(), className, className, property);
    }

    public String getSampleIndex() {
        return String.format("https://docs.gradle.org/%s/samples", gradleVersion.getVersion());
    }

    public String getSampleFor(String id) {
        return String.format("https://docs.gradle.org/%s/samples/sample_%s.html", gradleVersion.getVersion(), id);
    }
}