package ch.stvbero.platform.kern.anmeldung;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Markiert Ressourcen, die ohne Anmeldung erreichbar sind. Alles andere unter /api verlangt eine Sitzung. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Oeffentlich {
}
