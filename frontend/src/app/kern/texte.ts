/**
 * Texte der Oberfläche an einer Stelle, damit Tests und Doku dieselben Wörter verwenden
 * (docs/testbarkeit.md, Regel 2).
 */
export const TEXTE = {
  appName: 'STV Bero',
  navigation: {
    name: 'Hauptnavigation',
    start: 'Start',
    termine: 'Termine',
    teams: 'Teams',
    profil: 'Profil',
    verwaltung: 'Verwaltung',
  },
  start: {
    begruessung: (vorname: string) => `Hallo ${vorname}`,
    kachelnTitel: 'Was möchtest du tun?',
    keineKacheln: 'Für dich gibt es noch keine Kacheln.',
  },
  termine: {
    titel: 'Termine',
    leer: 'Keine kommenden Termine',
  },
  teams: {
    titel: 'Meine Teams',
    leer: 'Du bist noch in keinem Team.',
  },
  profil: {
    titel: 'Profil',
    darstellung: 'Darstellung',
    hell: 'Hell',
    dunkel: 'Dunkel',
    system: 'Wie System',
    abmelden: 'Abmelden',
  },
  verwaltung: {
    titel: 'Verwaltung',
    leer: 'Personen, Teams und Saisons verwaltest du hier, sobald diese Funktionen bereit sind.',
  },
  anmelden: {
    titel: 'Anmelden',
    verfahrenOffen: 'Die Anmeldung für alle Mitglieder wird gerade eingerichtet.',
    zumTestmodus: 'Zur Anmeldung im Testmodus',
  },
  devLogin: {
    titel: 'Anmelden als …',
    hinweis: 'Testmodus',
    erklaerung: 'Nur für Entwicklung und Tests. Ein Klick meldet dich als diese Person an.',
    listeName: 'Testpersonen',
    admin: 'Admin',
  },
  allgemein: {
    laden: 'Wird geladen …',
    keineBerechtigung: 'Keine Berechtigung',
    nichtGefunden: 'Diese Seite gibt es nicht.',
    zurStartseite: 'Zur Startseite',
    fehlerLaden: 'Das hat nicht geklappt. Bitte versuche es nochmals.',
  },
} as const;
