package jp.akimateras.jackson;

import java.beans.Introspector;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

final class NodeMapper {
    private final ObjectMapper mapper;
    private final DiscriminatorTypeResolver typeResolver;
    private final boolean defaultNonNull;

    NodeMapper(ObjectMapper mapper, DiscriminatorTypeResolver typeResolver) {
        this(mapper, typeResolver, true);
    }

    NodeMapper(ObjectMapper mapper, DiscriminatorTypeResolver typeResolver, boolean defaultNonNull) {
        this.mapper = mapper;
        this.typeResolver = typeResolver;
        this.defaultNonNull = defaultNonNull;
    }

    @Nullable Object mapNode(@Nullable JsonNode node, JavaType targetType) throws IOException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (targetType.getRawClass().isPrimitive()) {
                throw new IOException("Missing value for primitive " + targetType);
            }
            return null;
        }

        Class<?> raw = targetType.getRawClass();
        if (JsonNode.class.isAssignableFrom(raw)) {
            return node;
        }

        if (targetType.isMapLikeType()) {
            return mapMap(node, targetType);
        }
        if (targetType.isArrayType()) {
            return mapArray(node, targetType);
        }
        if (targetType.isCollectionLikeType()) {
            return mapCollection(node, targetType);
        }

        if (typeResolver.needsTypeResolution(raw)) {
            DiscriminatorTypeResolver.ResolvedType resolved = typeResolver.resolve(node, raw);
            JsonNode sanitized = typeResolver.stripDiscriminators(node, resolved.discriminatorsToRemove());
            return mapNode(sanitized, mapper.getTypeFactory().constructType(resolved.concreteType()));
        }
        if (raw.isRecord()) {
            return mapRecord(node, raw);
        }
        if (raw == Object.class) {
            return mapper.treeToValue(node, raw);
        }
        if (node.isObject()) {
            return mapPojo(node, raw);
        }
        return mapper.treeToValue(node, raw);
    }

    private Object mapMap(JsonNode node, JavaType targetType) throws IOException {
        if (!node.isObject()) {
            throw new IOException("Expected object for " + targetType);
        }
        JavaType keyType = targetType.getKeyType();
        if (keyType == null) {
            keyType = mapper.getTypeFactory().constructType(String.class);
        }
        JavaType valueType = targetType.getContentType();
        if (valueType == null) {
            valueType = mapper.getTypeFactory().constructType(Object.class);
        }
        Map<Object, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            Object key = mapKey(entry.getKey(), keyType);
            Object value = mapNode(entry.getValue(), valueType);
            values.put(key, value);
        }
        Class<?> raw = targetType.getRawClass();
        if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
            if (SortedMap.class.isAssignableFrom(raw)) {
                return new TreeMap<>(values);
            }
            return values;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) raw.getDeclaredConstructor().newInstance();
            map.putAll(values);
            return map;
        } catch (ReflectiveOperationException e) {
            Object converted = mapper.convertValue(values, raw);
            if (converted == null) {
                throw new IOException("Unable to construct map for " + raw.getName());
            }
            return converted;
        }
    }

    private Object mapKey(String key, JavaType keyType) throws IOException {
        Class<?> rawKey = keyType.getRawClass();
        if (rawKey == String.class || rawKey == Object.class) {
            return key;
        }
        try {
            return mapper.convertValue(key, keyType);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unable to map key '" + key + "' to " + keyType, e);
        }
    }

    private Object mapCollection(JsonNode node, JavaType targetType) throws IOException {
        if (!node.isArray()) {
            throw new IOException("Expected array for " + targetType);
        }
        JavaType contentType = targetType.getContentType();
        if (contentType == null) {
            contentType = mapper.getTypeFactory().constructType(Object.class);
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(mapNode(element, contentType));
        }
        Class<?> raw = targetType.getRawClass();
        if (List.class.isAssignableFrom(raw) || Collection.class.equals(raw)) {
            return values;
        }
        if (Set.class.isAssignableFrom(raw)) {
            return new LinkedHashSet<>(values);
        }
        return mapper.convertValue(values, raw);
    }

    private Object mapArray(JsonNode node, JavaType targetType) throws IOException {
        if (!node.isArray()) {
            throw new IOException("Expected array for " + targetType);
        }
        JavaType contentType = targetType.getContentType();
        if (contentType == null) {
            contentType = mapper.getTypeFactory().constructType(Object.class);
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(mapNode(element, contentType));
        }
        Class<?> componentRaw = contentType.getRawClass();
        Object array = Array.newInstance(componentRaw, values.size());
        for (int i = 0; i < values.size(); i++) {
            Array.set(array, i, values.get(i));
        }
        return array;
    }

    private <T> T mapRecord(JsonNode node, Class<T> recordType) throws IOException {
        Constructor<?> delegatingCtor = findDelegatingCreatorConstructor(recordType);
        Method delegatingFactory = findDelegatingCreatorFactoryMethod(recordType);
        if (delegatingCtor != null && delegatingFactory != null) {
            throw new IOException("Multiple delegating @JsonCreator creators for " + recordType.getName());
        }
        if (delegatingFactory != null) {
            Object value = mapPojoWithDelegatingFactory(node, recordType, delegatingFactory);
            return recordType.cast(value);
        }
        if (delegatingCtor != null) {
            Object value = mapPojoWithDelegatingConstructor(node, recordType, delegatingCtor);
            return recordType.cast(value);
        }
        if (!node.isObject()) {
            throw new IOException("Expected object for record " + recordType.getName());
        }
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] argTypes = new Class<?>[components.length];
        Set<String> consumed = new LinkedHashSet<>();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            argTypes[i] = component.getType();
            PropertyNames names = resolvePropertyNames(component, component.getName());
            JsonNode valueNode = findPropertyNode(node, names, consumed);
            JavaType componentType = mapper.getTypeFactory().constructType(component.getGenericType());
            Nullability nullability = effectiveNullability(resolveNullability(component), isRequired(component));
            if (valueNode == null) {
                if (component.getType().isPrimitive()) {
                    throw new IOException("Missing value for primitive record component " + names.primary());
                }
                if (nullability == Nullability.NULLABLE) {
                    args[i] = null;
                    continue;
                }
                throw new IOException("Missing value for non-null record component " + names.primary());
            }
            Object value = mapNode(valueNode, componentType);
            if (value == null && nullability != Nullability.NULLABLE) {
                throw new IOException("Null value for non-null record component " + names.primary());
            }
            args[i] = value;
        }
        try {
            Constructor<T> constructor = recordType.getDeclaredConstructor(argTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new IOException("Unable to construct record " + recordType.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to construct record " + recordType.getName(), e.getCause());
        }
    }

    private Object mapPojo(JsonNode node, Class<?> raw) throws IOException {
        if (!node.isObject()) {
            throw new IOException("Expected object for " + raw.getName());
        }
        BuilderInfo builderInfo = findBuilderInfo(raw);
        if (builderInfo != null) {
            return mapPojoWithBuilder(node, raw, builderInfo);
        }
        Method creatorFactory = findCreatorFactoryMethod(raw);
        Constructor<?> creator = findCreatorConstructor(raw);
        if (creatorFactory != null) {
            if (creator != null && creator.getAnnotation(JsonCreator.class) != null) {
                throw new IOException("Multiple @JsonCreator creators for " + raw.getName());
            }
            return mapPojoWithFactory(node, raw, creatorFactory);
        }
        if (creator != null && creator.getParameterCount() > 0) {
            return mapPojoWithConstructor(node, raw, creator);
        }
        if (hasDefaultConstructor(raw)) {
            return mapPojoWithDefaultConstructor(node, raw);
        }
        if (creator != null) {
            return mapPojoWithDefaultConstructor(node, raw);
        }
        return mapper.treeToValue(node, raw);
    }

    private Object mapPojoWithBuilder(JsonNode node, Class<?> raw, BuilderInfo builderInfo) throws IOException {
        Object builder = createBuilderInstance(raw, builderInfo);
        Set<String> consumed = new LinkedHashSet<>();
        List<PropertyBinding> builderBindings = collectBuilderBindings(builderInfo.builderClass(),
                builderInfo.withPrefix(), builderInfo.buildMethodName());
        applyBindings(builder, node, builderBindings, consumed);
        Object instance = invokeBuild(builder, builderInfo, raw);
        applyPropertyBindings(instance, node, raw, consumed);
        return instance;
    }

    private Object mapPojoWithConstructor(JsonNode node, Class<?> raw, Constructor<?> ctor) throws IOException {
        JsonCreator creator = ctor.getAnnotation(JsonCreator.class);
        if (creator != null && creator.mode() == JsonCreator.Mode.DELEGATING) {
            return mapPojoWithDelegatingConstructor(node, raw, ctor);
        }
        Parameter[] parameters = ctor.getParameters();
        Object[] args = new Object[parameters.length];
        Set<String> consumed = new LinkedHashSet<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            PropertyNames names = resolvePropertyNames(parameter);
            JsonNode valueNode = findPropertyNode(node, names, consumed);
            JavaType paramType = mapper.getTypeFactory().constructType(parameter.getParameterizedType());
            Nullability nullability = effectiveNullability(resolveNullability(parameter), isRequired(parameter));
            if (valueNode == null) {
                if (parameter.getType().isPrimitive()) {
                    throw new IOException("Missing value for primitive constructor parameter " + names.primary());
                }
                if (nullability == Nullability.NULLABLE) {
                    args[i] = null;
                    continue;
                }
                throw new IOException("Missing value for non-null constructor parameter " + names.primary());
            }
            Object value = mapNode(valueNode, paramType);
            if (value == null && nullability != Nullability.NULLABLE) {
                throw new IOException("Null value for non-null constructor parameter " + names.primary());
            }
            args[i] = value;
        }
        Object instance = instantiateConstructor(ctor, args);
        applyPropertyBindings(instance, node, raw, consumed);
        return instance;
    }

    private Object mapPojoWithDelegatingConstructor(JsonNode node, Class<?> raw, Constructor<?> ctor) throws IOException {
        if (ctor.getParameterCount() != 1) {
            throw new IOException("Delegating @JsonCreator constructor must have exactly one parameter for "
                    + raw.getName());
        }
        Parameter parameter = ctor.getParameters()[0];
        JavaType paramType = mapper.getTypeFactory().constructType(parameter.getParameterizedType());
        Object value = mapNode(node, paramType);
        Nullability nullability = effectiveNullability(resolveNullability(parameter), isRequired(parameter));
        if (value == null && nullability != Nullability.NULLABLE) {
            throw new IOException("Null value for non-null constructor parameter " + parameter.getName());
        }
        return instantiateConstructor(ctor, new Object[] { value });
    }

    private Object mapPojoWithFactory(JsonNode node, Class<?> raw, Method factory) throws IOException {
        JsonCreator creator = factory.getAnnotation(JsonCreator.class);
        if (creator != null && creator.mode() == JsonCreator.Mode.DELEGATING) {
            return mapPojoWithDelegatingFactory(node, raw, factory);
        }
        Parameter[] parameters = factory.getParameters();
        Object[] args = new Object[parameters.length];
        Set<String> consumed = new LinkedHashSet<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            PropertyNames names = resolvePropertyNames(parameter);
            JsonNode valueNode = findPropertyNode(node, names, consumed);
            JavaType paramType = mapper.getTypeFactory().constructType(parameter.getParameterizedType());
            Nullability nullability = effectiveNullability(resolveNullability(parameter), isRequired(parameter));
            if (valueNode == null) {
                if (parameter.getType().isPrimitive()) {
                    throw new IOException("Missing value for primitive factory parameter " + names.primary());
                }
                if (nullability == Nullability.NULLABLE) {
                    args[i] = null;
                    continue;
                }
                throw new IOException("Missing value for non-null factory parameter " + names.primary());
            }
            Object value = mapNode(valueNode, paramType);
            if (value == null && nullability != Nullability.NULLABLE) {
                throw new IOException("Null value for non-null factory parameter " + names.primary());
            }
            args[i] = value;
        }
        Object instance = invokeFactory(factory, args);
        applyPropertyBindings(instance, node, raw, consumed);
        return instance;
    }

    private Object mapPojoWithDelegatingFactory(JsonNode node, Class<?> raw, Method factory) throws IOException {
        if (factory.getParameterCount() != 1) {
            throw new IOException("Delegating @JsonCreator factory must have exactly one parameter for "
                    + raw.getName());
        }
        Parameter parameter = factory.getParameters()[0];
        JavaType paramType = mapper.getTypeFactory().constructType(parameter.getParameterizedType());
        Object value = mapNode(node, paramType);
        Nullability nullability = effectiveNullability(resolveNullability(parameter), isRequired(parameter));
        if (value == null && nullability != Nullability.NULLABLE) {
            throw new IOException("Null value for non-null factory parameter " + parameter.getName());
        }
        return invokeFactory(factory, new Object[] { value });
    }

    private Object mapPojoWithDefaultConstructor(JsonNode node, Class<?> raw) throws IOException {
        Object instance = instantiatePojo(raw);
        Set<String> consumed = new LinkedHashSet<>();
        applyPropertyBindings(instance, node, raw, consumed);
        return instance;
    }

    private void applyPropertyBindings(Object instance, JsonNode node, Class<?> raw, Set<String> consumed)
            throws IOException {
        List<PropertyBinding> bindings = collectPropertyBindings(raw);
        applyBindings(instance, node, bindings, consumed);
    }

    private void applyBindings(Object instance, JsonNode node, List<PropertyBinding> bindings, Set<String> consumed)
            throws IOException {
        for (PropertyBinding binding : bindings) {
            JsonNode valueNode = findPropertyNode(node, binding.names(), consumed);
            if (valueNode == null) {
                handleMissingBinding(instance, binding);
                continue;
            }
            Object value = mapNode(valueNode, binding.type());
            if (value == null) {
                handleNullBinding(instance, binding);
                continue;
            }
            binding.apply(instance, value);
        }
    }

    private void handleMissingBinding(Object instance, PropertyBinding binding) throws IOException {
        if (binding.type().getRawClass().isPrimitive()) {
            throw new IOException("Missing value for primitive property " + binding.names().primary());
        }
        Nullability nullability = effectiveNullability(resolveNullability(binding), isRequired(binding));
        if (nullability == Nullability.NULLABLE) {
            binding.apply(instance, null);
            return;
        }
        if (nullability == Nullability.NON_NULL) {
            throw new IOException("Missing value for non-null property " + binding.names().primary());
        }
        throw new IOException("Missing value for non-null property " + binding.names().primary());
    }

    private void handleNullBinding(Object instance, PropertyBinding binding) throws IOException {
        if (binding.type().getRawClass().isPrimitive()) {
            throw new IOException("Missing value for primitive property " + binding.names().primary());
        }
        Nullability nullability = effectiveNullability(resolveNullability(binding), isRequired(binding));
        if (nullability != Nullability.NULLABLE) {
            throw new IOException("Null value for non-null property " + binding.names().primary());
        }
        binding.apply(instance, null);
    }

    private @Nullable BuilderInfo findBuilderInfo(Class<?> raw) throws IOException {
        BuilderInfo annotationInfo = findBuilderInfoFromAnnotation(raw);
        if (annotationInfo != null) {
            return annotationInfo;
        }
        BuilderInfo factoryInfo = findBuilderInfoFromFactoryMethod(raw);
        if (factoryInfo != null) {
            return factoryInfo;
        }
        return findBuilderInfoFromNestedClass(raw);
    }

    private @Nullable BuilderInfo findBuilderInfoFromAnnotation(Class<?> raw) throws IOException {
        JsonDeserialize deserialize = raw.getAnnotation(JsonDeserialize.class);
        if (deserialize == null || deserialize.builder() == Void.class) {
            return null;
        }
        Class<?> builderClass = deserialize.builder();
        BuilderConfig config = resolveBuilderConfig(builderClass);
        Method buildMethod = findBuildMethod(builderClass, config.buildMethodName(), raw);
        Method factoryMethod = findBuilderFactory(raw, builderClass);
        return new BuilderInfo(builderClass, factoryMethod, buildMethod, config.withPrefix());
    }

    private @Nullable BuilderInfo findBuilderInfoFromFactoryMethod(Class<?> raw) throws IOException {
        Method builderFactory = null;
        for (Method method : getAllMethods(raw)) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (!name.equals("builder") && !name.equals("newBuilder")) {
                continue;
            }
            if (builderFactory != null) {
                throw new IOException("Multiple builder factory methods for " + raw.getName());
            }
            builderFactory = method;
        }
        if (builderFactory == null) {
            return null;
        }
        Class<?> builderClass = builderFactory.getReturnType();
        BuilderConfig config = resolveBuilderConfig(builderClass);
        Method buildMethod = findBuildMethodOptional(builderClass, config.buildMethodName(), raw);
        if (buildMethod == null) {
            return null;
        }
        return new BuilderInfo(builderClass, builderFactory, buildMethod, config.withPrefix());
    }

    private @Nullable BuilderInfo findBuilderInfoFromNestedClass(Class<?> raw) throws IOException {
        Class<?> builderClass = null;
        for (Class<?> nested : raw.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Builder") || nested.getSimpleName().equals(raw.getSimpleName() + "Builder")) {
                if (!Modifier.isStatic(nested.getModifiers())) {
                    continue;
                }
                if (builderClass != null) {
                    throw new IOException("Multiple nested builder classes for " + raw.getName());
                }
                builderClass = nested;
            }
        }
        if (builderClass == null) {
            return null;
        }
        BuilderConfig config = resolveBuilderConfig(builderClass);
        Method buildMethod = findBuildMethodOptional(builderClass, config.buildMethodName(), raw);
        if (buildMethod == null) {
            return null;
        }
        return new BuilderInfo(builderClass, null, buildMethod, config.withPrefix());
    }

    private BuilderConfig resolveBuilderConfig(Class<?> builderClass) {
        JsonPOJOBuilder annotation = builderClass.getAnnotation(JsonPOJOBuilder.class);
        String buildMethodName = "build";
        String withPrefix = "with";
        if (annotation != null) {
            if (!annotation.buildMethodName().isEmpty()) {
                buildMethodName = annotation.buildMethodName();
            }
            withPrefix = annotation.withPrefix();
        }
        return new BuilderConfig(buildMethodName, withPrefix);
    }

    private Method findBuildMethod(Class<?> builderClass, String buildMethodName, Class<?> raw) throws IOException {
        Method match = null;
        for (Method method : getAllMethods(builderClass)) {
            if (!method.getName().equals(buildMethodName) || method.getParameterCount() != 0) {
                continue;
            }
            if (!raw.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (match != null) {
                throw new IOException("Multiple build methods for " + builderClass.getName());
            }
            match = method;
        }
        if (match == null) {
            throw new IOException("Missing build method '" + buildMethodName + "' for " + builderClass.getName());
        }
        return match;
    }

    private @Nullable Method findBuildMethodOptional(Class<?> builderClass, String buildMethodName, Class<?> raw) {
        Method match = null;
        for (Method method : getAllMethods(builderClass)) {
            if (!method.getName().equals(buildMethodName) || method.getParameterCount() != 0) {
                continue;
            }
            if (!raw.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = method;
        }
        return match;
    }

    private @Nullable Method findBuilderFactory(Class<?> raw, Class<?> builderClass) {
        for (Method method : getAllMethods(raw)) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            if (!builderClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName();
            if (name.equals("builder") || name.equals("newBuilder")) {
                return method;
            }
        }
        return null;
    }

    private Object createBuilderInstance(Class<?> raw, BuilderInfo builderInfo) throws IOException {
        Method factory = builderInfo.factoryMethod();
        if (factory != null) {
            try {
                factory.setAccessible(true);
                Object builder = factory.invoke(null);
                if (builder == null) {
                    throw new IOException("Builder factory returned null for " + raw.getName());
                }
                return builder;
            } catch (IllegalAccessException e) {
                throw new IOException("Unable to invoke builder factory for " + raw.getName(), e);
            } catch (InvocationTargetException e) {
                throw new IOException("Builder factory failed for " + raw.getName(), e.getCause());
            }
        }
        Class<?> builderClass = builderInfo.builderClass();
        if (builderClass.isMemberClass() && !Modifier.isStatic(builderClass.getModifiers())) {
            throw new IOException("Non-static builder class for " + raw.getName());
        }
        try {
            Constructor<?> constructor = builderClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IOException("No default constructor for builder " + builderClass.getName(), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IOException("Unable to construct builder " + builderClass.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Builder constructor failed for " + builderClass.getName(), e.getCause());
        }
    }

    private Object invokeBuild(Object builder, BuilderInfo builderInfo, Class<?> raw) throws IOException {
        Method buildMethod = builderInfo.buildMethod();
        try {
            buildMethod.setAccessible(true);
            Object built = buildMethod.invoke(builder);
            if (built == null) {
                throw new IOException("Builder returned null for " + raw.getName());
            }
            return built;
        } catch (IllegalAccessException e) {
            throw new IOException("Unable to invoke build for " + raw.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Build method failed for " + raw.getName(), e.getCause());
        }
    }

    private List<PropertyBinding> collectBuilderBindings(Class<?> builderClass, String withPrefix,
            String buildMethodName) {
        Map<String, PropertyBinding> bindings = new LinkedHashMap<>();
        for (Method method : getAllMethods(builderClass)) {
            if (Modifier.isStatic(method.getModifiers()) || isIgnored(method)) {
                continue;
            }
            PropertyNames names = resolveBuilderPropertyNames(method, withPrefix, buildMethodName);
            if (names == null) {
                continue;
            }
            JavaType type = mapper.getTypeFactory().constructType(method.getGenericParameterTypes()[0]);
            bindings.putIfAbsent(names.primary(), new SetterBinding(names, method, type));
        }
        return new ArrayList<>(bindings.values());
    }

    private static @Nullable PropertyNames resolveBuilderPropertyNames(Method method, String withPrefix,
            String buildMethodName) {
        if (method.getParameterCount() != 1) {
            return null;
        }
        if (method.getName().equals(buildMethodName)) {
            return null;
        }
        JsonProperty property = method.getAnnotation(JsonProperty.class);
        JsonProperty paramProperty = method.getParameters()[0].getAnnotation(JsonProperty.class);
        String fallbackName = resolveBuilderPropertyName(method, withPrefix);
        String primary = resolvePrimaryName(fallbackName, property, paramProperty);
        if (primary == null || primary.isEmpty()) {
            return null;
        }
        JsonAlias alias = method.getAnnotation(JsonAlias.class);
        JsonAlias paramAlias = method.getParameters()[0].getAnnotation(JsonAlias.class);
        List<String> aliases = collectAliases(primary, alias, paramAlias);
        return new PropertyNames(primary, aliases);
    }

    private static String resolveBuilderPropertyName(Method method, String withPrefix) {
        String name = method.getName();
        if (withPrefix == null) {
            withPrefix = "";
        }
        if (withPrefix.isEmpty()) {
            return name;
        }
        if (name.startsWith(withPrefix) && name.length() > withPrefix.length()) {
            return Introspector.decapitalize(name.substring(withPrefix.length()));
        }
        return "";
    }

    private Object instantiatePojo(Class<?> raw) throws IOException {
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IOException("No default constructor for " + raw.getName(), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IOException("Unable to construct " + raw.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to construct " + raw.getName(), e.getCause());
        }
    }

    private Object instantiateConstructor(Constructor<?> ctor, Object[] args) throws IOException {
        try {
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IOException("Unable to construct " + ctor.getDeclaringClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to construct " + ctor.getDeclaringClass().getName(), e.getCause());
        }
    }

    private Object invokeFactory(Method factory, Object[] args) throws IOException {
        try {
            factory.setAccessible(true);
            return factory.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IOException("Unable to invoke factory " + factory.getDeclaringClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Factory failed " + factory.getDeclaringClass().getName(), e.getCause());
        }
    }

    private @Nullable Constructor<?> findCreatorConstructor(Class<?> raw) throws IOException {
        Constructor<?>[] constructors = raw.getDeclaredConstructors();
        Constructor<?> annotated = null;
        Constructor<?> noArgs = null;
        List<Constructor<?>> nonDefault = new ArrayList<>();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                noArgs = constructor;
            } else {
                nonDefault.add(constructor);
            }
            JsonCreator creator = constructor.getAnnotation(JsonCreator.class);
            if (creator != null) {
                if (annotated != null) {
                    throw new IOException("Multiple @JsonCreator constructors for " + raw.getName());
                }
                annotated = constructor;
            }
        }
        if (annotated != null) {
            return annotated;
        }
        if (noArgs != null) {
            return null;
        }
        if (nonDefault.size() == 1) {
            return nonDefault.get(0);
        }
        if (nonDefault.isEmpty()) {
            return null;
        }
        throw new IOException("No suitable constructor for " + raw.getName());
    }

    private @Nullable Method findCreatorFactoryMethod(Class<?> raw) throws IOException {
        Method annotated = null;
        for (Method method : getAllMethods(raw)) {
            if (method.getAnnotation(JsonCreator.class) == null) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!raw.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (annotated != null) {
                throw new IOException("Multiple @JsonCreator factory methods for " + raw.getName());
            }
            annotated = method;
        }
        return annotated;
    }

    private @Nullable Constructor<?> findDelegatingCreatorConstructor(Class<?> raw) throws IOException {
        Constructor<?> annotated = null;
        for (Constructor<?> constructor : raw.getDeclaredConstructors()) {
            JsonCreator creator = constructor.getAnnotation(JsonCreator.class);
            if (creator == null || creator.mode() != JsonCreator.Mode.DELEGATING) {
                continue;
            }
            if (annotated != null) {
                throw new IOException("Multiple delegating @JsonCreator constructors for " + raw.getName());
            }
            annotated = constructor;
        }
        return annotated;
    }

    private @Nullable Method findDelegatingCreatorFactoryMethod(Class<?> raw) throws IOException {
        Method annotated = null;
        for (Method method : getAllMethods(raw)) {
            JsonCreator creator = method.getAnnotation(JsonCreator.class);
            if (creator == null || creator.mode() != JsonCreator.Mode.DELEGATING) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!raw.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (annotated != null) {
                throw new IOException("Multiple delegating @JsonCreator factory methods for " + raw.getName());
            }
            annotated = method;
        }
        return annotated;
    }

    private static boolean hasDefaultConstructor(Class<?> raw) {
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor();
            return constructor != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private List<PropertyBinding> collectPropertyBindings(Class<?> raw) {
        Map<String, PropertyBinding> bindings = new LinkedHashMap<>();
        Set<String> setterTargets = new LinkedHashSet<>();
        for (Method method : getAllMethods(raw)) {
            if (isIgnored(method)) {
                continue;
            }
            String fallbackName = resolvePropertyNameFromMethod(method);
            if (!fallbackName.isEmpty()) {
                setterTargets.add(fallbackName);
            }
            PropertyNames names = resolvePropertyNames(method);
            if (names == null) {
                continue;
            }
            JavaType type = mapper.getTypeFactory().constructType(method.getGenericParameterTypes()[0]);
            bindings.putIfAbsent(names.primary(), new SetterBinding(names, method, type));
        }
        for (Field field : getAllFields(raw)) {
            if (isIgnored(field)) {
                continue;
            }
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (setterTargets.contains(field.getName())) {
                continue;
            }
            PropertyNames names = resolvePropertyNames(field, field.getName());
            JavaType type = mapper.getTypeFactory().constructType(field.getGenericType());
            bindings.putIfAbsent(names.primary(), new FieldBinding(names, field, type));
        }
        return new ArrayList<>(bindings.values());
    }

    private static boolean isIgnored(Field field) {
        return Modifier.isStatic(field.getModifiers()) || field.getAnnotation(JsonIgnore.class) != null;
    }

    private static boolean isIgnored(Method method) {
        return Modifier.isStatic(method.getModifiers()) || method.getAnnotation(JsonIgnore.class) != null;
    }

    private static List<Field> getAllFields(Class<?> raw) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = raw;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> raw) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = raw;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                methods.add(method);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    private static @Nullable PropertyNames resolvePropertyNames(Method method) {
        if (method.getParameterCount() != 1) {
            return null;
        }
        String fallbackName = resolvePropertyNameFromMethod(method);
        JsonProperty property = method.getAnnotation(JsonProperty.class);
        JsonProperty paramProperty = method.getParameters()[0].getAnnotation(JsonProperty.class);
        String primary = resolvePrimaryName(fallbackName, property, paramProperty);
        if (primary == null || primary.isEmpty()) {
            return null;
        }
        JsonAlias alias = method.getAnnotation(JsonAlias.class);
        JsonAlias paramAlias = method.getParameters()[0].getAnnotation(JsonAlias.class);
        List<String> aliases = collectAliases(primary, alias, paramAlias);
        return new PropertyNames(primary, aliases);
    }

    private static PropertyNames resolvePropertyNames(Field field, String fallbackName) {
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        String primary = Objects.requireNonNull(resolvePrimaryName(fallbackName, property, null));
        JsonAlias alias = field.getAnnotation(JsonAlias.class);
        List<String> aliases = collectAliases(primary, alias);
        return new PropertyNames(primary, aliases);
    }

    private static PropertyNames resolvePropertyNames(RecordComponent component, String fallbackName) {
        JsonProperty componentProperty = component.getAnnotation(JsonProperty.class);
        JsonAlias componentAlias = component.getAnnotation(JsonAlias.class);
        Method accessor = component.getAccessor();
        JsonProperty accessorProperty = accessor.getAnnotation(JsonProperty.class);
        JsonAlias accessorAlias = accessor.getAnnotation(JsonAlias.class);
        JsonProperty fieldProperty;
        JsonAlias fieldAlias;
        try {
            Field field = component.getDeclaringRecord().getDeclaredField(component.getName());
            fieldProperty = field.getAnnotation(JsonProperty.class);
            fieldAlias = field.getAnnotation(JsonAlias.class);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing record field for " + component.getName(), e);
        }
        String primary = resolvePrimaryNameForRecord(fallbackName, componentProperty, accessorProperty, fieldProperty);
        List<String> aliases = collectAliases(primary, componentAlias, accessorAlias, fieldAlias);
        return new PropertyNames(primary, aliases);
    }

    private static PropertyNames resolvePropertyNames(Parameter parameter) throws IOException {
        JsonProperty property = parameter.getAnnotation(JsonProperty.class);
        String primary = resolvePrimaryName(null, property, null);
        if (primary == null || primary.isEmpty()) {
            if (parameter.isNamePresent()) {
                primary = parameter.getName();
            } else {
                throw new IOException("Missing @JsonProperty for constructor parameter " + parameter.getName());
            }
        }
        JsonAlias alias = parameter.getAnnotation(JsonAlias.class);
        List<String> aliases = collectAliases(primary, alias);
        return new PropertyNames(primary, aliases);
    }

    private static @Nullable String resolvePrimaryName(@Nullable String fallback, @Nullable JsonProperty property,
            @Nullable JsonProperty parameterProperty) {
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }
        if (parameterProperty != null && !parameterProperty.value().isEmpty()) {
            return parameterProperty.value();
        }
        return fallback;
    }

    private static String resolvePrimaryNameForRecord(String fallback, @Nullable JsonProperty... properties) {
        for (JsonProperty property : properties) {
            if (property != null && !property.value().isEmpty()) {
                return property.value();
            }
        }
        return Objects.requireNonNull(fallback);
    }

    private static String resolvePropertyNameFromMethod(Method method) {
        String name = method.getName();
        if (name.startsWith("set") && name.length() > 3) {
            return Introspector.decapitalize(name.substring(3));
        }
        if (name.startsWith("with") && name.length() > 4) {
            return Introspector.decapitalize(name.substring(4));
        }
        if (isFluentSetter(method)) {
            return name;
        }
        return "";
    }

    private static boolean isFluentSetter(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return false;
        }
        return method.getDeclaringClass().isAssignableFrom(returnType);
    }

    private static List<String> collectAliases(String primary, @Nullable JsonAlias... aliasAnnotations) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (JsonAlias alias : aliasAnnotations) {
            if (alias == null) {
                continue;
            }
            for (String name : alias.value()) {
                if (name.isEmpty() || name.equals(primary)) {
                    continue;
                }
                aliases.add(name);
            }
        }
        return List.copyOf(aliases);
    }

    private static final Set<String> NULLABLE_ANNOTATIONS = Set.of(
            "org.jspecify.annotations.Nullable",
            "javax.annotation.Nullable",
            "jakarta.annotation.Nullable",
            "org.jetbrains.annotations.Nullable",
            "androidx.annotation.Nullable",
            "edu.umd.cs.findbugs.annotations.Nullable");

    private static final Set<String> NON_NULL_ANNOTATIONS = Set.of(
            "org.jspecify.annotations.NonNull",
            "javax.annotation.Nonnull",
            "jakarta.annotation.Nonnull",
            "org.jetbrains.annotations.NotNull",
            "androidx.annotation.NonNull",
            "edu.umd.cs.findbugs.annotations.NonNull",
            "lombok.NonNull",
            "jp.akimateras.jackson.NonNull");

    private enum Nullability {
        NULLABLE,
        NON_NULL,
        UNSPECIFIED
    }

    private Nullability effectiveNullability(Nullability nullability) {
        if (nullability != Nullability.UNSPECIFIED) {
            return nullability;
        }
        if (defaultNonNull) {
            return Nullability.NON_NULL;
        }
        return Nullability.NULLABLE;
    }

    private Nullability effectiveNullability(Nullability nullability, boolean required) {
        if (required) {
            return Nullability.NON_NULL;
        }
        return effectiveNullability(nullability);
    }

    private static boolean isRequired(PropertyBinding binding) {
        if (binding instanceof FieldBinding fieldBinding) {
            return isRequired(fieldBinding.field());
        }
        if (binding instanceof SetterBinding setterBinding) {
            Method method = setterBinding.method();
            Parameter parameter = method.getParameters()[0];
            return isRequired(method.getAnnotation(JsonProperty.class), parameter.getAnnotation(JsonProperty.class));
        }
        return false;
    }

    private static boolean isRequired(Parameter parameter) {
        return isRequired(parameter.getAnnotation(JsonProperty.class));
    }

    private static boolean isRequired(Field field) {
        return isRequired(field.getAnnotation(JsonProperty.class));
    }

    private static boolean isRequired(RecordComponent component) {
        JsonProperty property = component.getAnnotation(JsonProperty.class);
        return isRequired(property);
    }

    private static boolean isRequired(@Nullable JsonProperty property) {
        return property != null && property.required();
    }

    private static boolean isRequired(@Nullable JsonProperty property, @Nullable JsonProperty parameterProperty) {
        return isRequired(property) || isRequired(parameterProperty);
    }

    private static Nullability resolveNullability(PropertyBinding binding) {
        if (binding instanceof FieldBinding fieldBinding) {
            return resolveNullability(fieldBinding.field());
        }
        if (binding instanceof SetterBinding setterBinding) {
            Method method = setterBinding.method();
            Parameter parameter = method.getParameters()[0];
            return mergeNullability(resolveNullability(method), resolveNullability(parameter));
        }
        return Nullability.UNSPECIFIED;
    }

    private static Nullability resolveNullability(RecordComponent component) {
        Nullability nullability = resolveNullability((AnnotatedElement) component);
        nullability = mergeNullability(nullability, resolveNullability(component.getAccessor()));
        try {
            Field field = component.getDeclaringRecord().getDeclaredField(component.getName());
            nullability = mergeNullability(nullability, resolveNullability(field));
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing record field for " + component.getName(), e);
        }
        return nullability;
    }

    private static Nullability resolveNullability(Parameter parameter) {
        return resolveNullability((AnnotatedElement) parameter);
    }

    private static Nullability resolveNullability(Method method) {
        return resolveNullability((AnnotatedElement) method);
    }

    private static Nullability resolveNullability(Field field) {
        return resolveNullability((AnnotatedElement) field);
    }

    private static Nullability resolveNullability(AnnotatedElement element) {
        if (hasAnnotation(element, NULLABLE_ANNOTATIONS)) {
            return Nullability.NULLABLE;
        }
        if (hasAnnotation(element, NON_NULL_ANNOTATIONS)) {
            return Nullability.NON_NULL;
        }
        return Nullability.UNSPECIFIED;
    }

    private static boolean hasAnnotation(AnnotatedElement element, Set<String> names) {
        for (Annotation annotation : element.getAnnotations()) {
            if (names.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        if (element instanceof Field field) {
            return hasAnnotation(field.getAnnotatedType(), names);
        }
        if (element instanceof Parameter parameter) {
            return hasAnnotation(parameter.getAnnotatedType(), names);
        }
        if (element instanceof RecordComponent component) {
            return hasAnnotation(component.getAnnotatedType(), names);
        }
        return false;
    }

    private static boolean hasAnnotation(AnnotatedType annotatedType, Set<String> names) {
        for (Annotation annotation : annotatedType.getAnnotations()) {
            if (names.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Nullability mergeNullability(Nullability first, Nullability second) {
        if (first == Nullability.NULLABLE || second == Nullability.NULLABLE) {
            return Nullability.NULLABLE;
        }
        if (first == Nullability.NON_NULL || second == Nullability.NON_NULL) {
            return Nullability.NON_NULL;
        }
        return Nullability.UNSPECIFIED;
    }

    private static @Nullable JsonNode findPropertyNode(JsonNode node, PropertyNames names, Set<String> consumed) {
        for (String candidate : names.candidates()) {
            if (node.has(candidate) && !consumed.contains(candidate)) {
                consumed.add(candidate);
                return node.get(candidate);
            }
        }
        return null;
    }

    private record BuilderConfig(String buildMethodName, String withPrefix) {
    }

    private record BuilderInfo(Class<?> builderClass, @Nullable Method factoryMethod, Method buildMethod,
            String withPrefix) {
        String buildMethodName() {
            return buildMethod.getName();
        }
    }

    private record PropertyNames(String primary, List<String> aliases) {
        List<String> candidates() {
            if (aliases.isEmpty()) {
                return List.of(primary);
            }
            List<String> values = new ArrayList<>(1 + aliases.size());
            values.add(primary);
            values.addAll(aliases);
            return values;
        }
    }

    private sealed interface PropertyBinding permits FieldBinding, SetterBinding {
        PropertyNames names();

        JavaType type();

        void apply(Object target, @Nullable Object value) throws IOException;
    }

    private record FieldBinding(PropertyNames names, Field field, JavaType type) implements PropertyBinding {
        @Override
        public void apply(Object target, @Nullable Object value) throws IOException {
            try {
                field.setAccessible(true);
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new IOException("Unable to set field " + field.getName(), e);
            }
        }
    }

    private record SetterBinding(PropertyNames names, Method method, JavaType type) implements PropertyBinding {
        @Override
        public void apply(Object target, @Nullable Object value) throws IOException {
            try {
                method.setAccessible(true);
                method.invoke(target, value);
            } catch (IllegalAccessException e) {
                throw new IOException("Unable to invoke setter " + method.getName(), e);
            } catch (InvocationTargetException e) {
                throw new IOException("Setter failed " + method.getName(), e.getCause());
            }
        }
    }
}
