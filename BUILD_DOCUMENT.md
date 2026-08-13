# R4 – Byggedokument

**Status:** Planlagt / klar for utvikling  
**Plattform v1:** Android  
**Arbeidsnavn:** R4  
**Utvikler:** Morning Coffee Labs

## 1. Bakgrunn

R4 oppstod fra et konkret behov hos ledere i mobilspill som Kingshot.

R4/R5-ledere i allianser sender ofte de samme eller lignende meldingene gjentatte ganger: informasjon om events, regler, påminnelser, instrukser, strategier og annen alliansekommunikasjon.

Å hente disse tekstene fra andre apper eller skrive dem på nytt krever at brukeren forlater spillet eller bytter mellom apper.

R4 skal løse dette ved å være en liten, flytende tekstsamling som kan ligge over appen brukeren arbeider i.

Selv om Kingshot er utgangspunktet, skal R4 ikke bygges som en Kingshot-spesifikk app. Samme funksjon kan være nyttig i andre spill og apper hvor brukeren ofte trenger tilgang til ferdigskrevne tekster.

## 2. Kjerneidé

Brukeren oppretter og lagrer tekstmeldinger i R4.

Hver melding består i v1 av:

- Tittel
- Tekst

Brukeren skal kunne skrive teksten direkte i R4 eller lime inn en eksisterende tekst fra utklippstavlen.

Når overlay-modus er aktiv, ligger R4 som et lite flyttbart element over andre Android-apper.

Brukeren kan:

1. Opprette et notat i R4 ved å skrive teksten direkte eller lime inn eksisterende tekst.
2. Gi notatet en tittel og lagre det.
3. Åpne R4-overlayen over appen som brukes.
4. Se titlene på lagrede meldinger.
5. Trykke på ønsket tittel.
6. Hele teksten kopieres automatisk til Androids utklippstavle.
7. Overlayen minimeres.
8. Brukeren limer teksten inn i appen som ligger under.

R4 skal ikke skrive, sende eller lime inn meldingen automatisk i andre apper.

## 3. Produktfilosofi

R4 skal være et lite verktøy som gjør én oppgave svært effektivt.

Prinsipper for v1:

- Raskt.
- Enkelt.
- Lokalt.
- Ingen konto.
- Ingen backend.
- Ingen nødvendig internettilkobling.
- Ingen AI.
- Ingen automatisering av andre apper.
- Minimalt antall trykk.
- Overlayen skal være minst mulig i veien når den ikke brukes.
- Brukerens tekst skal bevares nøyaktig slik den er skrevet eller limt inn.

R4 skal være et verktøy brukeren betjener, ikke en bot.

## 4. Plattform

### Android

R4 v1 utvikles kun for Android.

Planlagt teknisk retning:

- Kotlin
- Jetpack Compose
- Native Android
- Lokal datalagring
- Android overlay-funksjonalitet

Native Android velges fordi overlay-funksjonen er selve kjernen i produktet.

### iOS

iOS er ikke del av v1.

iOS tillater ikke tredjepartsapper å oppføre seg som en generell interaktiv overlay over andre apper på samme måte som Android.

En eventuell iOS-versjon må derfor løse arbeidsflyten på en annen måte og behandles som et separat fremtidig prosjekt/problem.

## 5. Hovedapp

Når R4 åpnes normalt, skal brukeren få tilgang til administrasjon av meldingene sine.

### Meldingsliste

Viser lagrede meldinger.

Primært vises:

- Tittel

Fra denne visningen skal brukeren kunne:

- Opprette ny melding
- Åpne eksisterende melding
- Redigere melding
- Slette melding

Eksakt UI avgjøres under utviklingen.

## 6. Opprette melding

Brukeren velger **Ny melding**.

Meldingen inneholder:

### Tittel

Kort navn som identifiserer meldingen.

Eksempler:

- Bear
- KvK
- Event Reminder
- Alliance Rules
- Recruitment

### Tekst

Selve teksten som senere skal kopieres.

Brukeren skal kunne:

- skrive teksten direkte
- lime inn eksisterende tekst

Deretter lagres meldingen lokalt.

### Tekststøtte og tekstintegritet

R4 skal støtte vanlig Unicode-tekst gjennom hele arbeidsflyten, inkludert emojis og andre spesialtegn.

R4 skal bevare innholdet nøyaktig slik brukeren skrev eller limte det inn. Dette gjelder blant annet:

- emojis
- Unicode-tegn
- linjeskift
- flere påfølgende linjeskift
- tomme linjer
- mellomrom
- flere påfølgende mellomrom
- innrykk
- symboler og spesialtegn
- ASCII-art

R4 skal ikke automatisk:

- trimme linjer
- fjerne ledende eller avsluttende mellomrom
- slå sammen linjeskift
- normalisere formatering
- rette tekst
- endre tegn eller emojis

Teksten som kopieres ut av R4 skal være den samme teksten som ble lagret.

Dette kravet gjelder hele kjeden:

**Skriv / lim inn → lagre → åpne → redigere → lagre på nytt → kopiere → clipboard → lime inn i annen app.**

## 7. Redigere melding

Eksisterende meldinger skal kunne åpnes og redigeres.

Brukeren skal kunne endre:

- Tittel
- Tekst

Endringene lagres lokalt.

Redigering skal følge samme krav til Unicode-, emoji- og tekstintegritet som ved opprettelse.

## 8. Slette melding

En melding skal kunne slettes.

Det skal være vanskelig å slette en melding ved et uhell. Endelig løsning for bekreftelse bestemmes under UI-utviklingen.

## 9. Overlay-modus

Overlay-modus er R4s viktigste funksjon.

Når overlay aktiveres, skal R4 kunne vises over en annen Android-app uten at denne appen må avsluttes eller legges bort.

Eksempel:

Kingshot kjører normalt på skjermen mens R4 ligger som et lite flytende element over spillet.

## 10. Minimert overlay

Når R4 ikke brukes aktivt, skal overlayen være liten.

Foreløpig konsept:

```text
┌────┐
│ R4 │
└────┘
```

Elementet skal kunne dras rundt på skjermen.

Brukeren bestemmer dermed selv hvor R4 ligger slik at den ikke dekker viktige knapper eller informasjon i appen under.

## 11. Flytting

Overlayen skal være dragbar.

Brukeren holder på R4-elementet og flytter det til ønsket sted på skjermen.

R4 skal:

- følge fingerbevegelsen naturlig
- ikke kunne forsvinne permanent utenfor skjermen
- huske siste plassering

Ved neste bruk skal overlayen i utgangspunktet åpnes på samme sted.

Mulig automatisk snapping til skjermkant vurderes senere og er ikke et krav i første versjon.

## 12. Utvidet overlay

Når brukeren trykker på den minimerte R4-overlayen, åpnes meldingslisten.

Eksempel:

```text
┌──────────────────────┐
│ R4                   │
├──────────────────────┤
│ Bear                 │
│ KvK                  │
│ Alliance Rules       │
│ Event Reminder       │
│ Recruitment          │
└──────────────────────┘
```

Hovedmålet er å gjøre listen:

- lett å lese
- rask å bruke
- liten nok til ikke å dominere skjermen

I overlay-modus er tittelen det viktige.

Brukeren skal ikke behøve å åpne eller lese hele teksten for å kopiere den.

## 13. Kopiering

Når brukeren trykker på en meldingstittel i overlay-modus:

1. Hele meldingsteksten kopieres til Androids utklippstavle.
2. Teksten skal kopieres nøyaktig slik den er lagret, inkludert emojis, linjeskift, mellomrom, innrykk og ASCII-art.
3. Brukeren får en kort visuell bekreftelse, eksempelvis `Copied`.
4. Overlayen minimeres igjen.

Brukeren kan deretter trykke i tekstfeltet i appen under og bruke vanlig **Lim inn / Paste**.

Dette gir eksempelvis følgende arbeidsflyt i Kingshot:

**R4 → Bear → Copied → Kingshot chat → Paste → Send**

## 14. Ingen direkte integrasjon

R4 skal ikke:

- lese Kingshot-chat
- sende meldinger
- trykke på knapper i andre apper
- automatisk lime inn tekst
- overvåke brukerens aktivitet
- automatisere spill
- fungere som bot

R4 skal kun gi brukeren rask tilgang til egne lagrede tekster og kopiere valgt tekst til systemets utklippstavle.

Dette prinsippet skal også gjelde når R4 brukes sammen med andre apper.

## 15. Lokal lagring

Alle meldinger lagres lokalt på enheten i v1.

Minimum datamodell:

```text
Message
id
title
text
createdAt
updatedAt
```

Lagringsløsningen skal lagre tekstinnhold uten tap eller utilsiktet transformasjon av Unicode-tegn, emojis, linjeskift eller mellomrom.

Ingen Firebase eller annen ekstern database er nødvendig.

## 16. Tillatelser

R4 trenger Androids nødvendige tillatelse for å kunne vises over andre apper.

Brukeren må aktivt godkjenne denne tillatelsen.

Appen skal forklare kort og tydelig hvorfor tillatelsen er nødvendig.

Overlay-tillatelsen skal kun brukes til R4s synlige brukerbetjente overlay.

## 17. Offline

R4s kjernefunksjonalitet skal fungere uten internett.

Brukeren skal kunne:

- opprette meldinger
- redigere meldinger
- slette meldinger
- åpne overlay
- kopiere meldinger

uten nettforbindelse.

## 18. Ikke del av v1

Følgende skal ikke legges inn som del av v1 uten at det tas en ny beslutning:

- Brukerkonto
- Firebase
- Cloud backup
- Synkronisering mellom enheter
- Deling av meldingsbibliotek mellom brukere
- AI
- Automatisk oversettelse
- Automatisk sending
- Integrasjon med Kingshot
- Integrasjon med Discord
- Kalender
- Planlagte meldinger
- Meldingsanalyse
- iOS-overlay
- Webversjon
- Desktopversjon

Disse funksjonene er eventuelle fremtidige utvidelser.

## 19. Ting som kan vurderes etter praktisk testing

Disse punktene er bevisst ikke låst:

- Overlay-størrelse
- Justerbar størrelse
- Automatisk snapping til skjermkant
- Sortering av meldinger
- Mapper/kategorier
- Favoritter
- Søk
- Overlay-transparens
- Automatisk plassering
- Hvor mye av tittelen som vises
- Antall meldinger som vises før scrolling
- Manuell kontra automatisk minimering etter kopiering

Beslutninger tas primært ut fra faktisk bruk.

## 20. Første testbruker

Første prototype skal bygges rundt et reelt bruksscenario:

**R4/R5-ledelse i Kingshot.**

Målet er ikke først og fremst å bevise at teknologien fungerer. Målet er å finne ut om R4 faktisk reduserer tiden og irritasjonen forbundet med gjentatt alliansekommunikasjon.

Første praktiske test bør derfor være:

Bruk R4 aktivt sammen med Kingshot over flere dager og noter hva som fungerer, hva som er irriterende og hva som mangler.

Funksjoner bør ikke legges til bare fordi de virker nyttige i teorien.

Testingen skal også inkludere meldinger som inneholder:

- emojis
- flere linjer
- tomme linjer
- innrykk
- flere mellomrom
- ASCII-art
- kombinasjoner av disse

Målet er å verifisere at teksten som limes inn i Kingshot er identisk med teksten som ble lagret i R4.

## 21. Potensielt marked

Kingshot er utgangspunktet, men ikke nødvendigvis sluttmarkedet.

Mulige brukergrupper inkluderer:

- Kingshot R4/R5
- Alliance-/guild-/clan-ledere i andre mobilspill
- MMO-spillere
- Moderatorer
- Community-ledere
- Brukere som gjentatte ganger sender standardsvar
- Personer som arbeider mellom apper og ofte trenger de samme tekstene

R4 bør derfor bygges generisk nok til at appen ikke teknisk er avhengig av Kingshot.

## 22. Navn

R4 brukes som arbeidsnavn.

Navnet kommer fra lederrollen R4 i spill som Kingshot og representerer problemet som opprinnelig skapte appidéen.

Endelig produktnavn avgjøres senere dersom appen skal markedsføres mot et bredere publikum.

Navnediskusjon skal ikke forsinke utvikling av første prototype.

## 23. V1 – definisjon av ferdig

R4 v1 kan regnes som funksjonelt komplett når brukeren kan:

1. Installere appen på Android.
2. Opprette en melding med tittel og tekst.
3. Skrive teksten direkte eller lime inn eksisterende tekst.
4. Lagre meldingen uten at tekstinnhold eller formatering endres.
5. Se lagrede meldinger.
6. Redigere dem uten tap av tekstformat.
7. Slette dem.
8. Aktivere overlay-modus.
9. Flytte overlayen rundt på skjermen.
10. Minimere og åpne overlayen.
11. Se lagrede titler i overlayen.
12. Trykke på en tittel.
13. Få hele teksten kopiert til clipboard.
14. Få emojis, Unicode-tegn, linjeskift, tomme linjer, mellomrom, innrykk og ASCII-art bevart ved kopiering.
15. Få bekreftelse på kopieringen.
16. Fortsette å bruke appen som ligger under overlayen.
17. Lime inn den kopierte teksten der.
18. Verifisere at den innlimte teksten er identisk med teksten som ble lagret i R4.

Når dette fungerer stabilt, har R4 oppfylt sitt opprinnelige formål.

## 24. Videre utvikling

Etter første fungerende prototype skal videre prioritering baseres på praktisk bruk.

Det viktigste spørsmålet er:

**Blir arbeidsflyten merkbart bedre med R4 enn uten R4?**

Hvis svaret er ja, kan prosjektet videreutvikles og vurderes som et offentlig Morning Coffee Labs-produkt.

Hvis svaret er nei, skal vi først forbedre kjerneopplevelsen fremfor å kompensere med flere funksjoner.

## Prosjektregel

Dette dokumentet definerer planlagt scope for R4 v1.

Funksjonalitet som legges til utover dette dokumentet skal behandles som nytt eller utvidet scope, ikke som noe som manglet fra den opprinnelige planen.

Dokumentet kan oppdateres når vi bevisst beslutter å endre planen.
