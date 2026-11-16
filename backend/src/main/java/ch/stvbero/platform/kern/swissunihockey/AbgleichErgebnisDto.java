package ch.stvbero.platform.kern.swissunihockey;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Ergebnis eines Abgleichs (SUH-03).
 *
 * @param neu                neu angelegte Spiele
 * @param geaendert          übernommene Änderungen (Zeit, Halle, Gegner, Heim/Auswärts, Resultat, wieder geliefert)
 * @param nichtMehrVorhanden Spiele, die swiss unihockey nicht mehr liefert (markiert, nicht gelöscht)
 * @param unveraendert       Spiele ohne Änderung
 * @param betroffeneTermine  angelegte oder geänderte Spieltage
 */
@Schema(name = "AbgleichErgebnis")
public record AbgleichErgebnisDto(int neu, int geaendert, int nichtMehrVorhanden, int unveraendert,
                                  List<UUID> betroffeneTermine) {
}
