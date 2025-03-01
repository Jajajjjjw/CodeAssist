package com.tyron.builder.internal.reflect.service;


import java.lang.reflect.Method;

class ReflectionBasedServiceMethodFactory implements ServiceMethodFactory {
    @Override
    public ServiceMethod toServiceMethod(Method method) {
        return new ReflectionBasedServiceMethod(method);
    }
}