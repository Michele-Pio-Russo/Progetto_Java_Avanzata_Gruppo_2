package guesstheword.common;

/**
 * Implementazione del Cifrario di Cesare.
 * Cifra e decifra stringhe sostituendo ogni lettera con quella
 * che si trova k posizioni avanti nell'alfabeto.
 */
public class CaesarCipher {

    private CaesarCipher() { /* utility class */ }

    /**
     * Cifra una parola con lo spostamento dato.
     * Solo le lettere (A-Z, a-z) vengono spostate; altri caratteri rimangono invariati.
     *
     * @param word  parola da cifrare
     * @param shift spostamento (1–25)
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
     * Decifra una parola cifrata con il cifrario di Cesare.
     *
     * @param word  parola cifrata
     * @param shift spostamento originale usato per la cifratura
     * @return parola originale
     */
    public static String decrypt(String word, int shift) {
        return encrypt(word, 26 - shift);
    }
}
