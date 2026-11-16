package ch.stvbero.platform.core.swissunihockey;

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
@Schema(name = "SyncResult")
public record SyncResultDto(int created, int changed, int missing, int unchanged,
                                  List<UUID> affectedEvents) {
}
