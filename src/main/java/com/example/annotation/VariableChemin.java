package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface VariableChemin {
    String value() default ""; // Le nom du path param (défaut : nom de l'argument Java)
    boolean required() default true; // Optionnel : si obligatoire
}