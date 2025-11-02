package com.example.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

// Définition de l'annotation
@Retention(RetentionPolicy.RUNTIME) // L’annotation sera disponible à l’exécution
@Target(ElementType.TYPE)            // TYPE = applicable sur une classe, interface ou enum
public @interface AnnotationController {
    String url() default ""; // attribut obligatoire
}
