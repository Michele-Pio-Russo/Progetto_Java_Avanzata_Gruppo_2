package indovinaparola.common;

/**
 * Implementazione del Cifrario di Cesare.
 * Sostituisce ogni lettera con quella che si trova {@code spostamento} posizioni
 * avanti nell'alfabeto (A-Z, a-z). Tutti gli altri caratteri rimangono invariati.
 *
 * <p>Classe di utilità — costruttore privato.</p>
 */
public final class CifrarioCesare {

    private CifrarioCesare() {
        // utility class — non istanziabile
    }

    /**
     * Cifra una parola con il Cifrario di Cesare.
     * Ogni lettera viene spostata di {@code spostamento} posizioni in avanti.
     * Lettere maiuscole e minuscole vengono gestite separatamente.
     * Caratteri non alfabetici rimangono invariati.
     *
     * @param parola  parola da cifrare (non null)
     * @param spostamento numero di posizioni di spostamento (1–25)
     * @return parola cifrata
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
     * Decifra una parola cifrata con il Cifrario di Cesare.
     * Equivale a cifrare con spostamento inverso (26 - spostamento).
     *
     * @param parola  parola cifrata (non null)
     * @param spostamento spostamento originale usato per la cifratura (1–25)
     * @return parola originale
     */
    public static String decifra(String parola, int spostamento) {
        return cifra(parola, 26 - spostamento);
    }
}
