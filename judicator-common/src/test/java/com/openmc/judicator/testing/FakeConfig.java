package com.openmc.judicator.testing;

import org.spongepowered.configurate.ConfigurationNode;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link ConfigurationNode} built from plain maps and lists, so code that reads configuration can be tested
 * without files. Only the read methods Judicator uses are implemented.
 */
public final class FakeConfig {

    private FakeConfig() {
    }

    /**
     * @param values nested {@code Map<String, Object>}, {@code List<Object>} and scalar values
     */
    public static ConfigurationNode of(Map<String, ?> values) {
        return node(values);
    }

    /**
     * Small helper for building nested maps: {@code map("a", 1, "b", map("c", true))}.
     */
    public static Map<String, Object> map(Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static ConfigurationNode node(Object value) {
        return (ConfigurationNode) Proxy.newProxyInstance(FakeConfig.class.getClassLoader(),
                new Class<?>[]{ConfigurationNode.class}, (proxy, method, args) -> {
                    final Object[] a = args == null ? new Object[0] : args;
                    switch (method.getName()) {
                        case "node": {
                            final Object[] path = a.length == 1 && a[0] instanceof Object[] array ? array : a;
                            Object current = value;
                            for (Object key : path) {
                                current = current instanceof Map<?, ?> map ? map.get(String.valueOf(key)) : null;
                            }
                            return node(current);
                        }
                        case "getString":
                            return value == null || value instanceof Map || value instanceof List
                                    ? (a.length > 0 ? a[0] : null) : String.valueOf(value);
                        case "getBoolean":
                            return value == null ? (a.length > 0 ? a[0] : false) : Boolean.parseBoolean(String.valueOf(value));
                        case "getInt":
                            try {
                                return value == null ? (a.length > 0 ? a[0] : 0) : Integer.parseInt(String.valueOf(value).trim());
                            } catch (NumberFormatException e) {
                                return a.length > 0 ? a[0] : 0; // Configurate falls back to the default too
                            }
                        case "getLong":
                            try {
                                return value == null ? (a.length > 0 ? a[0] : 0L) : Long.parseLong(String.valueOf(value).trim());
                            } catch (NumberFormatException e) {
                                return a.length > 0 ? a[0] : 0L;
                            }
                        case "isNull":
                        case "virtual":
                            return value == null;
                        case "childrenList": {
                            final List<ConfigurationNode> children = new ArrayList<>();
                            if (value instanceof List<?> list) list.forEach(item -> children.add(node(item)));
                            return children;
                        }
                        case "childrenMap": {
                            final Map<Object, ConfigurationNode> children = new LinkedHashMap<>();
                            if (value instanceof Map<?, ?> map) map.forEach((k, v) -> children.put(k, node(v)));
                            return children;
                        }
                        case "getList":
                            // Configurate returns a scalar as a one-element list.
                            if (value == null) return null;
                            return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>(List.of(value));
                        case "toString":
                            return "FakeConfig" + Arrays.toString(new Object[]{value});
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == a[0];
                        default:
                            throw new UnsupportedOperationException("FakeConfig does not implement " + method.getName());
                    }
                });
    }
}
