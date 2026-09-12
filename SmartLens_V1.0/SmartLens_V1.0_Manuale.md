# SmartLens V1.0 — Manuale utente e tecnico

## Versione
**Progetto:** SmartLens_V1.0  
**Server:** SmartLens V1.0  
**Client Android:** SmartLens 1.0  
**Manager Windows:** SmartLens Manager V1.0

## Struttura
SmartLens è composto da:
- server Python/Flask per acquisizione, storico, controllo e automazioni;
- console Administrator Windows per avvio/arresto server, porta reale, client attivi, log e utenti;
- client Android SmartLens per accesso da smartphone.

Per preservare storico, credenziali e configurazioni delle versioni precedenti, la cartella dati interna resta quella storica di BatteryLens.

## Account e ruoli
L'account principale `administrator` viene creato al primo avvio della console sul PC server. Dopo la prima configurazione, la console locale entra direttamente e avvia automaticamente il server.

Gli utenti possono essere:
- **Amministratore:** visualizza Stato, Automazioni, Controllo e Impostazioni.
- **Client:** visualizza soltanto Stato.

La console Administrator permette creazione utenti, selezione ruolo tramite slide, cambio password, eliminazione utenti non principali e disconnessione forzata dei client.

## Connessione
### Porta reale del server
La porta effettiva è configurabile esclusivamente nella console Administrator. Il valore è salvato in `server_runtime.json` e diventa effettivo al successivo avvio del server.

### Parametri destinati all'APK
In Impostazioni → Connessione server per APK vengono memorizzati indirizzo e porta destinati al client. Questi valori sono salvati in `apk_connection.json` e **non modificano** la configurazione reale del server.

Nel client Android, indirizzo e porta vengono memorizzati localmente sul telefono dopo il test di connessione.

## Client Android
Al primo avvio:
1. inserire indirizzo e porta del server;
2. premere Test connessione;
3. il client verifica SmartLens;
4. se il server è configurato apre la schermata login;
5. inserire nome utente e password creati dall'amministratore.

L'app non apre un browser esterno: usa una WebView incorporata.

## Controllo inverter
La scheda Controllo mostra solo le informazioni essenziali:
- nome del parametro;
- valore;
- unità;
- Read;
- Send.

Sono stati rimossi i commenti tecnici aggiuntivi come ID e range. La spiegazione generale è ridotta a: **Leggi o modifica i parametri dell'inverter.**

## Valori standard nelle caselle vuote
Gli esempi personali sono stati eliminati. I placeholder usano valori generici, per esempio:
- server: `192.168.1.100`;
- porta: `5000`;
- valori numerici standard senza prefisso “es.”.

## Sicurezza
Le API amministrative e le sezioni riservate rispettano i ruoli. I client normali non possono accedere alle funzioni amministrative anche richiamando direttamente gli endpoint.

## Storico modifiche
### SmartLens V1.0
- rinominato il progetto da BatteryLens a SmartLens;
- versione applicazione impostata a 1.0;
- rimossi riferimenti personali negli esempi;
- sostituiti i placeholder con valori standard;
- semplificata la scheda Controllo;
- mantenuta separazione tra parametri APK e porta reale server;
- mantenuta gestione multiutente e ruoli;
- creato progetto Android finale;
- predisposta compilazione APK e EXE Windows.
