/*
 * Copyright 2015 Victor Albertos
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

package io.rx_cache.internal;

import java.lang.reflect.Method;

import javax.inject.Inject;

import io.rx_cache.DynamicKey;
import io.rx_cache.DynamicKeyGroup;
import io.rx_cache.EvictDynamicKey;
import io.rx_cache.EvictDynamicKeyGroup;
import io.rx_cache.EvictProvider;
import io.rx_cache.Expirable;
import io.rx_cache.LifeCache;
import io.rx_cache.Reply;
import rx.Observable;

public final class ProxyTranslator {
    private Method method;
    private Object[] objectsMethod;

    @Inject ProxyTranslator() {}

    ConfigProvider processMethod(Method method, Object[] objectsMethod) {
        this.method = method;
        this.objectsMethod = objectsMethod;

        ConfigProvider configProvider = new ConfigProvider(getProviderKey(), getDynamicKey(), getDynamicKeyGroup(), getLoaderObservable(),
                getLifeTimeCache(), requiredDetailResponse(), evictProvider(), getExpirable());
        checkIntegrityConfiguration(configProvider);

        processMethodHasBeenCalled = true;
        return configProvider;
    }

    protected String getProviderKey() {
        return method.getName();
    }

    protected String getDynamicKey() {
        DynamicKey dynamicKey = getObjectFromMethodParam(DynamicKey.class);
        if (dynamicKey != null) return dynamicKey.getDynamicKey().toString();

        DynamicKeyGroup dynamicKeyGroup = getObjectFromMethodParam(DynamicKeyGroup.class);
        if (dynamicKeyGroup != null) return dynamicKeyGroup.getDynamicKey().toString();

        return "";
    }

    protected String getDynamicKeyGroup() {
        DynamicKeyGroup dynamicKeyGroup = getObjectFromMethodParam(DynamicKeyGroup.class);
        return dynamicKeyGroup != null ? dynamicKeyGroup.getGroup().toString() : "";
    }

    protected Observable getLoaderObservable() {
        Observable observable = getObjectFromMethodParam(Observable.class);
        if (observable != null) return observable;

        String errorMessage = method.getName() + Locale.NOT_OBSERVABLE_LOADER_FOUND;
        throw new IllegalArgumentException(errorMessage);
    }

    protected long getLifeTimeCache() {
        LifeCache lifeCache = method.getAnnotation(LifeCache.class);
        if (lifeCache == null) return 0;
        return lifeCache.timeUnit().toMillis(lifeCache.duration());
    }

    protected boolean getExpirable() {
        Expirable expirable = method.getAnnotation(Expirable.class);
        if (expirable != null) return expirable.value();
        return true;
    }

    protected boolean requiredDetailResponse() {
        if (method.getReturnType() != Observable.class) {
            String errorMessage = method.getName() + Locale.INVALID_RETURN_TYPE;
            throw new IllegalArgumentException(errorMessage);
        }

        return method.getGenericReturnType().toString().contains(Reply.class.getName());
    }

    protected EvictProvider evictProvider() {
        EvictProvider evictProvider = getObjectFromMethodParam(EvictProvider.class);
        if (evictProvider != null) return evictProvider;
        else return new EvictProvider(false);
    }

    protected <T> T getObjectFromMethodParam(Class<T> expectedClass) {
        int countSameObjectsType = 0;
        T expectedObject = null;

        for (Object objectParam: objectsMethod) {
            if (expectedClass.isAssignableFrom(objectParam.getClass())) {
                expectedObject = (T) objectParam;
                countSameObjectsType++;
            }
        }

        if (countSameObjectsType > 1) {
            String errorMessage = method.getName() + Locale.JUST_ONE_INSTANCE + expectedObject.getClass().getSimpleName();
            throw new IllegalArgumentException(errorMessage);
        }

        return expectedObject;
    }

    private void checkIntegrityConfiguration(ConfigProvider configProvider) {
        if (configProvider.evictProvider() instanceof EvictDynamicKeyGroup
                && configProvider.getDynamicKeyGroup().isEmpty()) {
            String errorMessage = method.getName() + Locale.EVICT_DYNAMIC_KEY_GROUP_PROVIDED_BUT_NOT_PROVIDED_ANY_DYNAMIC_KEY_GROUP;
            throw new IllegalArgumentException(errorMessage);
        }

        if (configProvider.evictProvider() instanceof EvictDynamicKey
                && configProvider.getDynamicKey().isEmpty()) {
            String errorMessage = method.getName() + Locale.EVICT_DYNAMIC_KEY_PROVIDED_BUT_NOT_PROVIDED_ANY_DYNAMIC_KEY;
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public final static class ConfigProvider {
        private final String providerKey, dynamicKey, dynamicKeyGroup;
        private final Observable loaderObservable;
        private final long lifeTime;
        private final boolean requiredDetailedResponse;
        private final EvictProvider evictProvider;
        private final boolean expirable;

        public ConfigProvider(String providerKey, String dynamicKey, String group, Observable loaderObservable, long lifeTime, boolean requiredDetailedResponse, EvictProvider evictProvider, boolean expirable) {
            this.providerKey = providerKey;
            this.dynamicKey = dynamicKey;
            this.dynamicKeyGroup = group;
            this.loaderObservable = loaderObservable;
            this.lifeTime = lifeTime;
            this.evictProvider = evictProvider;
            this.requiredDetailedResponse = requiredDetailedResponse;
            this.expirable = expirable;
        }

        public String getProviderKey() {
            return providerKey;
        }

        public String getDynamicKey() {
            return dynamicKey;
        }

        public String getDynamicKeyGroup() {
            return dynamicKeyGroup;
        }

        public long getLifeTimeMillis() {
            return lifeTime;
        }

        boolean requiredDetailedResponse() {
            return requiredDetailedResponse;
        }

        Observable getLoaderObservable() {
            return loaderObservable;
        }

        public EvictProvider evictProvider() {
            return evictProvider;
        }

        public boolean isExpirable() {
            return expirable;
        }
    }

    //Exits for testing purposes
    private boolean processMethodHasBeenCalled;
    public boolean processMethodHasBeenCalled() {
        return processMethodHasBeenCalled;
    }
}
