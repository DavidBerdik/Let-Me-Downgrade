/*
 * This file is part of LSPosed. (https://github.com/LSPosed/LSPosed/blob/master/core/src/main/java/de/robv/android/xposed/XposedHelpers.java)
 *
 * Adapted by David Berdik for use in Let Me Downgrade.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */
package com.berdik.letmedowngrade.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class XposedHelpers {
    private static final ConcurrentHashMap<MemberCacheKey.Field, Optional<Field>> fieldCache = new ConcurrentHashMap<>();

    /**
     * Note that we use object key instead of string here, because string calculation will lose all
     * the benefits of 'HashMap', this is basically the solution of performance traps.
     * <p>
     * So in fact we only need to use the structural comparison results of the reflection object.
     *
     * @see <a href="https://github.com/RinOrz/LSPosed/blob/a44e1f1cdf0c5e5ebfaface828e5907f5425df1b/benchmark/src/result/ReflectionCacheBenchmark.json">benchmarks for ART</a>
     * @see <a href="https://github.com/meowool-catnip/cloak/blob/main/api/src/benchmark/kotlin/com/meowool/cloak/ReflectionObjectAccessTests.kt#L37-L65">benchmarks for JVM</a>
     */
    private abstract static class MemberCacheKey {
        private final int hash;

        protected MemberCacheKey(int hash) {
            this.hash = hash;
        }

        @Override
        public abstract boolean equals(@Nullable Object obj);

        @Override
        public final int hashCode() {
            return hash;
        }

        static final class Field extends MemberCacheKey {
            private final Class<?> clazz;
            private final String name;

            public Field(Class<?> clazz, String name) {
                super(Objects.hash(clazz, name));
                this.clazz = clazz;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Field field)) return false;
                return Objects.equals(clazz, field.clazz) && Objects.equals(name, field.name);
            }

            @NonNull
            @Override
            public String toString() {
                return clazz.getName() + "#" + name;
            }
        }
    }

    /**
     * Look up a field in a class and set it to accessible.
     *
     * @param clazz     The class which either declares or inherits the field.
     * @param fieldName The field name.
     * @return A reference to the field.
     * @throws NoSuchFieldError In case the field was not found.
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        MemberCacheKey.Field key = new MemberCacheKey.Field(clazz, fieldName);

        return fieldCache.computeIfAbsent(key, k -> {
            try {
                Field newField = findFieldRecursiveImpl(k.clazz, k.name);
                newField.setAccessible(true);
                return Optional.of(newField);
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchFieldError(key.toString()));
    }

    private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz.equals(Object.class))
                    break;

                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw e;
        }
    }

    /**
     * Returns the value of an object field in the given object instance. A class reference is not sufficient! See also {@link #findField}.
     */
    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    /**
     * For inner classes, returns the surrounding instance, i.e. the {@code this} reference of the surrounding class.
     */
    public static Object getSurroundingThis(Object obj) {
        return getObjectField(obj, "this$0");
    }
}