package com.intel.podm.mappers;

import org.modelmapper.Condition;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.modelmapper.config.Configuration;
import org.modelmapper.spi.PropertyInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.intel.podm.mappers.Converters.refConverter;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static org.modelmapper.convention.MatchingStrategies.STRICT;

/**
 * Base class for automated mappers.
 *
 * @param <S> Source class
 * @param <T> Target Class
 */
public abstract class BrilliantMapper<S, T> implements Mapper<S, T> {
    private final Map<Class<?>, Function> providers = new HashMap<>();
    private final List<TypeMap> typeMaps = new ArrayList<>();
    private final Set<String> ignoredProperties = new HashSet<>();
    private final Class<S> sourceClass;
    private final Class<T> targetClass;
    private ModelMapper mapper;

    protected BrilliantMapper(Class<S> sourceClass, Class<T> targetClass) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        mapper = new ModelMapper();

        configureMapper();
        configureTypeMaps();
        setDefaultMappingConditions();
    }

    private void configureMapper() {
        Configuration configuration = mapper.getConfiguration();
        configuration.setProvider(request -> {
            Function requestedTypeProvider = providers.get(request.getRequestedType());
            return requestedTypeProvider == null ? null : requestedTypeProvider.apply(request.getSource());
        });

        configuration.getConverters().add(refConverter());
        configuration.setMatchingStrategy(STRICT);
    }

    private void configureTypeMaps() {
        typeMaps.clear();
        typeMaps.addAll(createTypeMaps(getSourceClass(), getTargetClass()));
    }

    public final void setDefaultMappingConditions() {
        setMappingConditions();
    }

    public final void setMappingConditions(Condition... conditions) {
        Configuration configuration = mapper.getConfiguration();
        Collection<Condition> configuredConditions = new ArrayList<>();
        configuredConditions.add(ignoredPropertiesCondition());
        configuredConditions.addAll(asList(conditions));
        configuration.setPropertyCondition(context -> configuredConditions.stream().allMatch(condition -> condition.applies(context)));
    }

    @Override
    public void map(S source, T target) {
        typeMaps.forEach(typeMap -> typeMap.map(source, target));
        performNotAutomatedMapping(source, target);
    }

    @Override
    public Class<S> getSourceClass() {
        return sourceClass;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetClass;
    }

    protected void performNotAutomatedMapping(S source, T target) {
    }

    protected final <X, Y> void registerProvider(Class<Y> targetClass, Function<X, Y> targetClassProvider) {
        providers.put(targetClass, targetClassProvider);
    }

    public final void clearIgnoredProperties() {
        ignoredProperties.clear();
    }

    public final void ignoredProperties(String... props) {
        clearIgnoredProperties();
        ignoredProperties.addAll(asList(props));
    }

    /**
     * Creates type map for each interface in inheritance hierarchy.
     *
     * @param source source Class
     * @param target target Class
     * @return List of TypeMap objects for each interface in inheritance hierarchy.
     */
    private List<TypeMap> createTypeMaps(Class<?> source, Class<?> target) {
        List<TypeMap> maps = new ArrayList<>();

        Optional<TypeMap> typeMapOption = ofNullable(mapper.getTypeMap(source, target));
        TypeMap typeMap = typeMapOption.orElseGet(() -> mapper.createTypeMap(source, target));

        boolean isAnythingToMap = !typeMap.getMappings().isEmpty();
        if (isAnythingToMap) {
            maps.add(typeMap);
        }

        List<Class<?>> interfaces = asList(source.getInterfaces());
        interfaces.forEach(_interface -> maps.addAll(createTypeMaps(_interface, target)));

        return maps;
    }

    private Condition<?, ?> ignoredPropertiesCondition() {
        return context -> negate(
            context.getMapping().getDestinationProperties().stream()
                .map(PropertyInfo::getName)
                .filter(Objects::nonNull)
                .anyMatch(ignoredProperties::contains)
        );
    }
}
