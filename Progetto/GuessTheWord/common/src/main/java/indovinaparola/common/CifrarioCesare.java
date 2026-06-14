/**
* @file CifrarioCesare.java
* 
* @brief Implementazione del Cifrario di Cesare
* Questa classe crea tutta la logica dietro la cifratura della parola da indovinare
* 
* @author Gruppo 2
* 
* @version 1.0
*/
package indovinaparola.common;

public final class CifrarioCesare {

    private CifrarioCesare() {
        // utility class — non istanziabile
    }

    /**
     * @brief Cifra una parola con il Cifrario di Cesare.
     * 
     * Ogni lettera viene spostata di {@code spostamento} posizioni in avanti.
     * Le lettere maiuscole e minuscole vengono gestite separatamente.
     * I caratteri non alfabetici rimangono invariati.
     *
     * @param[in] parola  parola da cifrare (non null)
     * @param[in] spostamento numero di posizioni di spostamento (1–25)
     * @return (String) la parola cifrata
     */
    public static String cifra(String parola, int spostamento) {
        StringBuilder sb = new StringBuilder();
        for (char c : parola.toCharArray()) {
            if (Character.isLetter(c)) {
                char base = Character.isUpperCase(c) ? 'A' : 'a';
                sb.append((char) ((c - base + spostamento) % 26 + base));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * @brief Decifra una parola cifrata con il Cifrario di Cesare.
     * Cifra con spostamento inverso (26 - spostamento).
     *
     * @param[in] parola  parola cifrata (non null)
     * @param[in] spostamento spostamento originale usato per la cifratura (1–25)
     * @return (String) parola originale
     */
    public static String decifra(String parola, int spostamento) {
        return cifra(parola, 26 - spostamento);
    }
}
