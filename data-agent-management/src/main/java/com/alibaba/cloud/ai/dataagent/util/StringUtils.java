/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class StringUtils {

    private StringUtils() {
    }

    public static List<String> splitToList(String str, String separator) {
        String[] res = org.apache.commons.lang3.StringUtils.split(str, separator);
        if (res == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(res);
    }

    @SafeVarargs
    public static <T> String join(final T... elements) {
        return join(",", elements);
    }

    @SafeVarargs
    public static <T> String join(String delimiter, final T... elements) {
        return org.apache.commons.lang3.StringUtils.join(elements, delimiter);
    }

    public static <T> String join(String delimiter, Collection<T> elements) {
        return org.apache.commons.lang3.StringUtils.join(elements, delimiter);
    }
}
