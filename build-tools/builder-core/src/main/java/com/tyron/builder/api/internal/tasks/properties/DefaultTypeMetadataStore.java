package com.tyron.builder.api.internal.tasks.properties;

import static com.tyron.builder.api.internal.reflect.AnnotationCategory.TYPE;
import static com.tyron.builder.api.internal.reflect.validation.Severity.ERROR;
import static com.tyron.builder.api.internal.tasks.properties.ModifierAnnotationCategory.NORMALIZATION;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.tyron.builder.api.internal.GeneratedSubclasses;
import com.tyron.builder.internal.reflect.AnnotationCategory;
import com.tyron.builder.internal.reflect.PropertyMetadata;
import com.tyron.builder.internal.reflect.annotations.PropertyAnnotationMetadata;
import com.tyron.builder.internal.reflect.annotations.TypeAnnotationMetadata;
import com.tyron.builder.internal.reflect.annotations.TypeAnnotationMetadataStore;
import com.tyron.builder.internal.reflect.problems.ValidationProblemId;
import com.tyron.builder.internal.reflect.validation.ReplayingTypeValidationContext;
import com.tyron.builder.internal.reflect.validation.TypeValidationContext;
import com.tyron.builder.api.internal.tasks.properties.annotations.AbstractOutputPropertyAnnotationHandler;
import com.tyron.builder.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import com.tyron.builder.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import com.tyron.builder.api.tasks.Classpath;
import com.tyron.builder.api.tasks.CompileClasspath;
import com.tyron.builder.api.tasks.InputFiles;
import com.tyron.builder.cache.internal.CrossBuildInMemoryCache;
import com.tyron.builder.cache.internal.CrossBuildInMemoryCacheFactory;

import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class DefaultTypeMetadataStore implements TypeMetadataStore {
    private final Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers;
    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> propertyAnnotationHandlers;
    private final ImmutableSet<Class<? extends Annotation>> allowedPropertyModifiers;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;
    private final String displayName;
    private final Function<Class<?>, TypeMetadata> typeMetadataFactory = this::createTypeMetadata;

    public DefaultTypeMetadataStore(
            Collection<? extends TypeAnnotationHandler> typeAnnotationHandlers,
            Collection<? extends PropertyAnnotationHandler> propertyAnnotationHandlers,
            Collection<Class<? extends Annotation>> allowedPropertyModifiers,
            TypeAnnotationMetadataStore typeAnnotationMetadataStore,
            CrossBuildInMemoryCacheFactory cacheFactory
    ) {
        this.typeAnnotationHandlers = ImmutableSet.copyOf(typeAnnotationHandlers);
        this.propertyAnnotationHandlers = Maps
                .uniqueIndex(propertyAnnotationHandlers, PropertyAnnotationHandler::getAnnotationType);
        this.allowedPropertyModifiers = ImmutableSet.copyOf(allowedPropertyModifiers);
        this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        this.displayName = calculateDisplayName(propertyAnnotationHandlers);
        this.cache = cacheFactory.newClassCache();
    }

    private static String calculateDisplayName(Iterable<? extends PropertyAnnotationHandler> annotationHandlers) {
        for (PropertyAnnotationHandler annotationHandler : annotationHandlers) {
            if (annotationHandler instanceof AbstractOutputPropertyAnnotationHandler) {
                return "an input or output annotation";
            }
        }
        return "an input annotation";
    }

    @Override
    public <T> TypeMetadata getTypeMetadata(Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

    private <T> TypeMetadata createTypeMetadata(Class<T> type) {
        Class<?> publicType = GeneratedSubclasses.unpack(type);
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();
        TypeAnnotationMetadata annotationMetadata = typeAnnotationMetadataStore.getTypeAnnotationMetadata(publicType);
        annotationMetadata.visitValidationFailures(validationContext);

        for (TypeAnnotationHandler annotationHandler : typeAnnotationHandlers) {
            if (annotationMetadata.isAnnotationPresent(annotationHandler.getAnnotationType())) {
                annotationHandler.validateTypeMetadata(publicType, validationContext);
            }
        }

        ImmutableSet.Builder<PropertyMetadata> effectiveProperties = ImmutableSet.builderWithExpectedSize(annotationMetadata.getPropertiesAnnotationMetadata().size());
        for (PropertyAnnotationMetadata propertyAnnotationMetadata : annotationMetadata.getPropertiesAnnotationMetadata()) {
            Map<AnnotationCategory, Annotation> propertyAnnotations = propertyAnnotationMetadata.getAnnotations();
            Annotation typeAnnotation = propertyAnnotations.get(TYPE);
            Annotation normalizationAnnotation = propertyAnnotations.get(NORMALIZATION);
            Class<? extends Annotation> propertyType = determinePropertyType(typeAnnotation, normalizationAnnotation);
            if (propertyType == null) {
                validationContext.visitPropertyProblem(problem ->
                        problem.withId(ValidationProblemId.MISSING_ANNOTATION)
                                .forProperty(propertyAnnotationMetadata.getPropertyName())
                                .reportAs(ERROR)
                                .withDescription(() -> "is missing " + displayName)
                                .happensBecause("A property without annotation isn't considered during up-to-date checking")
                                .addPossibleSolution(() -> "Add " + displayName)
                                .addPossibleSolution("Mark it as @Internal")
                                .documentedAt("validation_problems", "missing_annotation")
                );
                continue;
            }

            PropertyAnnotationHandler annotationHandler = propertyAnnotationHandlers.get(propertyType);
            if (annotationHandler == null) {
                validationContext.visitPropertyProblem(problem ->
                        problem.withId(ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT)
                                .forProperty(propertyAnnotationMetadata.getPropertyName())
                                .reportAs(ERROR)
                                .withDescription(() -> String.format("is annotated with invalid property type @%s", propertyType.getSimpleName()))
                                .happensBecause(() -> "The '@" + propertyType.getSimpleName() + "' annotation cannot be used in this context")
                                .addPossibleSolution("Remove the property")
                                .addPossibleSolution(() -> "Use a different annotation, e.g one of " + toListOfAnnotations(propertyAnnotationHandlers.keySet()))
                                .documentedAt("validation_problems", "annotation_invalid_in_context")
                );
                continue;
            }

            ImmutableSet<? extends AnnotationCategory> allowedModifiersForPropertyType = annotationHandler.getAllowedModifiers();
            for (Map.Entry<AnnotationCategory, Annotation> entry : propertyAnnotations.entrySet()) {
                AnnotationCategory annotationCategory = entry.getKey();
                if (annotationCategory == TYPE) {
                    continue;
                }
                Class<? extends Annotation> annotationType = entry.getValue().annotationType();
                if (!allowedModifiersForPropertyType.contains(annotationCategory)) {
                    validationContext.visitPropertyProblem(problem ->
                            problem.withId(ValidationProblemId.INCOMPATIBLE_ANNOTATIONS)
                                    .forProperty(propertyAnnotationMetadata.getPropertyName())
                                    .reportAs(ERROR)
                                    .withDescription(() -> "is annotated with @" + annotationType.getSimpleName() + " but that is not allowed for '" + propertyType.getSimpleName() + "' properties")
                                    .happensBecause(() -> "This modifier is used in conjunction with a property of type '" + propertyType.getSimpleName() + "' but this doesn't have semantics")
                                    .withLongDescription(() -> "The list of allowed modifiers for '" + propertyType.getSimpleName() + "' is " + toListOfAnnotations(allowedPropertyModifiers))
                                    .addPossibleSolution(() -> "Remove the '@" + annotationType.getSimpleName() + "' annotation")
                                    .documentedAt("validation_problems", "incompatible_annotations"));
                } else if (!allowedPropertyModifiers.contains(annotationType)) {
                    validationContext.visitPropertyProblem(problem ->
                            problem.withId(ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT)
                                    .forProperty(propertyAnnotationMetadata.getPropertyName())
                                    .reportAs(ERROR)
                                    .withDescription(() -> String.format("is annotated with invalid modifier @%s", annotationType.getSimpleName()))
                                    .happensBecause(() -> "The '@" + annotationType.getSimpleName() + "' annotation cannot be used in this context")
                                    .addPossibleSolution("Remove the annotation")
                                    .addPossibleSolution(() -> "Use a different annotation, e.g one of " + toListOfAnnotations(allowedPropertyModifiers))
                                    .documentedAt("validation_problems", "annotation_invalid_in_context")
                    );
                }
            }

            PropertyMetadata property = new DefaultPropertyMetadata(propertyType, propertyAnnotationMetadata);
            annotationHandler.validatePropertyMetadata(property, validationContext);

            if (annotationHandler.isPropertyRelevant()) {
                effectiveProperties.add(property);
            }
        }
        return new DefaultTypeMetadata(effectiveProperties.build(), validationContext, propertyAnnotationHandlers);
    }

    private static String toListOfAnnotations(ImmutableSet<Class<? extends Annotation>> classes) {
        return classes.stream()
                .map(Class::getSimpleName)
                .map(s -> "@" + s)
                .sorted()
                .collect(forDisplay());
    }

    @Nullable
    private Class<? extends Annotation> determinePropertyType(@Nullable Annotation typeAnnotation, @Nullable Annotation normalizationAnnotation) {
        if (typeAnnotation != null) {
            return typeAnnotation.annotationType();
        } else if (normalizationAnnotation != null) {
            if (normalizationAnnotation.annotationType().equals(Classpath.class)
                || normalizationAnnotation.annotationType().equals(CompileClasspath.class)) {
                return InputFiles.class;
            }
        }
        return null;
    }

    private static class DefaultTypeMetadata implements TypeMetadata {
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;
        private final ReplayingTypeValidationContext validationProblems;
        private final ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers;

        DefaultTypeMetadata(
                ImmutableSet<PropertyMetadata> propertiesMetadata,
                ReplayingTypeValidationContext validationProblems,
                ImmutableMap<Class<? extends Annotation>, ? extends PropertyAnnotationHandler> annotationHandlers
        ) {
            this.propertiesMetadata = propertiesMetadata;
            this.validationProblems = validationProblems;
            this.annotationHandlers = annotationHandlers;
        }

        @Override
        public void visitValidationFailures(@Nullable String ownerPropertyPath, TypeValidationContext validationContext) {
            validationProblems.replay(ownerPropertyPath, validationContext);
        }

        @Override
        public Set<PropertyMetadata> getPropertiesMetadata() {
            return propertiesMetadata;
        }

        @Override
        public boolean hasAnnotatedProperties() {
            return !propertiesMetadata.isEmpty();
        }

        @Override
        public PropertyAnnotationHandler getAnnotationHandlerFor(PropertyMetadata propertyMetadata) {
            return annotationHandlers.get(propertyMetadata.getPropertyType());
        }
    }

    private static class DefaultPropertyMetadata implements PropertyMetadata {

        private final Class<? extends Annotation> propertyType;
        private final PropertyAnnotationMetadata annotationMetadata;

        public DefaultPropertyMetadata(Class<? extends Annotation> propertyType, PropertyAnnotationMetadata annotationMetadata) {
            this.propertyType = propertyType;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public String getPropertyName() {
            return annotationMetadata.getPropertyName();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return annotationMetadata.isAnnotationPresent(annotationType);
        }

        @Nullable
        @Override
        public Annotation getAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotations().get(category);
        }

        @Override
        public boolean hasAnnotationForCategory(AnnotationCategory category) {
            return annotationMetadata.getAnnotations().get(category) != null;
        }

        @Override
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public Method getGetterMethod() {
            return annotationMetadata.getMethod();
        }

        @Override
        public String toString() {
            return String.format("@%s %s", propertyType.getSimpleName(), getPropertyName());
        }
    }

    private static Collector<? super String, ?, String> forDisplay() {
        return Collectors.collectingAndThen(Collectors.toList(), stringList -> {
            if (stringList.isEmpty()) {
                return "";
            }
            if (stringList.size() == 1) {
                return stringList.get(0);
            }
            int bound = stringList.size() - 1;
            return String.join(", ", stringList.subList(0, bound)) + " or " + stringList.get(bound);
        });
    }
}