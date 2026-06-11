package guesstheword.common;

/**
 * Implementazione del Cifrario di Cesare.
 * Sostituisce ogni lettera con quella che si trova {@code shift} posizioni
 * avanti nell'alfabeto (A-Z, a-z). Tutti gli altri caratteri rimangono invariati.
 *
 * <p>Classe di utilità — costruttore privato.</p>
 */
public final class CaesarCipher {

    private CaesarCipher() {
        // utility class — non istanziabile
    }

    /**
     * Cifra una parola con il Cifrario di Cesare.
     * Ogni lettera viene spostata di {@code shift} posizioni in avanti.
     * Lettere maiuscole e minuscole vengono gestite separatamente.
     * Caratteri non alfabetici rimangono invariati.
     *
     * @param word  parola da cifrare (non null)
     * @param shift numero di posizioni di spostamento (1–25)
     * @return parola cifrata
     */
    public static String encrypt(String word, int shift) {
        StringBuilder sb = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (Character.isLetter(c)) {
                char base = Character.isUpperCase(c) ? 'A' : 'a';
                sb.append((char) ((c - base + shift) % 26 + base));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Decifra una parola cifrata con il Cifrario di Cesare.
     * Equivale a cifrare con spostamento inverso (26 - shift).
     *
     * @param word  parola cifrata (non null)
     * @param shift spostamento originale usato per la cifratura (1–25)
     * @return parola originale
     */
    public static String decrypt(String word, int shift) {
        return encrypt(word, 26 - shift);
    }
}
