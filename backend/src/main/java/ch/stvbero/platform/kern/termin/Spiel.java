package ch.stvbero.platform.kern.termin;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Einzelne Partie eines Spieltags mit Gegner, Anspielzeit, Heim/Auswärts und Resultat (AUF-14, AUF-15). */
@Entity
@Table(name = "spiel")
public class Spiel extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "termin_id")
    public Termin termin;

    public String gegner;

    public Instant anspielzeit;

    public boolean heim;

    /** Heim:Gast, z.B. «5:3». */
    public String resultat;

    @Enumerated(EnumType.STRING)
    public Quelle quelle = Quelle.MANUELL;

    @Column(name = "su_spiel_id")
    public Integer suSpielId;

    @Column(name = "bei_su_nicht_mehr_vorhanden")
    public boolean beiSuNichtMehrVorhanden;

    public static Spiel neu(Termin termin) {
        Spiel s = new Spiel();
        s.id = UUID.randomUUID();
        s.termin = termin;
        return s;
    }

    /** Spiele eines Spieltags nach Anspielzeit (AUF-14). */
    public static List<Spiel> von(UUID terminId) {
        return list("termin.id = ?1 order by anspielzeit, gegner, id", terminId);
    }

    public SpielDto dto() {
        return new SpielDto(id, gegner, TerminLeser.zeit(anspielzeit), heim, resultat, quelle, suSpielId,
                beiSuNichtMehrVorhanden);
    }
}
