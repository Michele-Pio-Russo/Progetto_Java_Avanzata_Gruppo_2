Account predefiniti per il testing di IndovinaLaParola:

--- ACCOUNT AMMINISTRATORE ---
Utile per l'accesso all'interfaccia di gestione server
Username: admin
Password: admin123

--- ACCOUNT UTENTI (PLAYERS) ---
Utili per testare subito una sfida completa tra due client
Giocatore 1: 
- Username: giocatore1 
- Password: pass1

Giocatore 2: 
- Username: giocatore2 
- Password: pass2


--- ISTRUZIONI DI AVVIO ---
Essendo applicazioni grafiche (JavaFX) che producono log su terminale, l'esecuzione di "java -jar" terrà bloccata la console attiva. 

Per testare l'ambiente simulando un server e due client connessi simultaneamente, avete due opzioni:

OPZIONE 1 (Finestre separate)
1. Aprire un terminale ed eseguire:  java -jar server.jar
2. Aprire un SECONDO terminale ed eseguire:  java -jar client.jar
3. Aprire un TERZO terminale ed eseguire:  java -jar client.jar

OPZIONE 2 (Comando 'start' su Windows)
Per lanciare tutto dalla stessa finestra senza bloccarla, digitare in sequenza:
start java -jar server.jar
start java -jar client.jar
start java -jar client.jar
