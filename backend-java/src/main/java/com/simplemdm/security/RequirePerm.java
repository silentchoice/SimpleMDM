package com.simplemdm.security;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    String value();

    int departmentArgument() default -1;
}
