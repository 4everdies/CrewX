package crewx.accountmanager.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Generates plausible player names for a new offline (cracked) alt: a first name with a number, in a few
 * styles players actually use ("Pedro7319", "lucas_2846", "RafaSilva27", "Bruno2009"...). Pure Java, so it is
 * unit-tested; it only produces a name - adding the account is up to the Alt Manager.
 */
public final class RandomAltName {
    public static final int MAX_LENGTH = 16;
    private static final int MAX_ATTEMPTS = 64;
    /** First names used recently are skipped, so consecutive clicks do not repeat the same name. */
    private static final int RECENT_NAMES = 24;

    static final String[] FIRST_NAMES = {
            "Joao", "Pedro", "Lucas", "Rafael", "Marcos", "Gabriel", "Matheus", "Gustavo", "Felipe", "Bruno",
            "Thiago", "Leonardo", "Rodrigo", "Diego", "Vinicius", "Caio", "Igor", "Andre", "Daniel", "Eduardo",
            "Henrique", "Arthur", "Samuel", "Enzo", "Davi", "Miguel", "Heitor", "Bernardo", "Otavio", "Murilo",
            "Renan", "Danilo", "Fabio", "Leandro", "Julio", "Victor", "Vitor", "Kaique", "Kauan", "Ryan",
            "Erick", "Yuri", "Luan", "Alan", "Wesley", "Willian", "Douglas", "Renato", "Ricardo", "Paulo",
            "Sergio", "Marcelo", "Alexandre", "Rogerio", "Fernando", "Luiz", "Carlos", "Nicolas", "Lorenzo", "Benicio",
            "Theo", "Joaquim", "Emanuel", "Isaac", "Pietro", "Anthony", "Vicente", "Raul", "Breno", "Cau\u00e3",
            "Ana", "Maria", "Julia", "Beatriz", "Larissa", "Camila", "Fernanda", "Leticia", "Amanda", "Bruna",
            "Mariana", "Gabriela", "Isabela", "Laura", "Sofia", "Helena", "Valentina", "Alice", "Manuela", "Luiza",
            "Yasmin", "Carolina", "Rafaela", "Vitoria", "Livia", "Clara", "Bianca", "Natalia", "Giovanna", "Lara",
            "Diogo", "Hugo", "Tomas", "Mateo", "Santiago", "Martin", "Alex", "Max", "Leo", "Noah"
    };
    static final String[] SHORT_NAMES = {
            "Rafa", "Gabi", "Dudu", "Gui", "Lipe", "Teteu", "Biel", "Nando", "Edu", "Vini",
            "Juju", "Bia", "Mari", "Duda", "Lari", "Nick", "Davi", "Kaka", "Leo", "Theo"
    };
    static final String[] SURNAMES = {
            "Silva", "Santos", "Souza", "Lima", "Costa", "Rocha", "Alves", "Gomes", "Ramos", "Melo",
            "Dias", "Lopes", "Nunes", "Pires", "Reis", "Moura", "Farias", "Prado", "Torres", "Viana"
    };

    private final Random random;
    private final Deque<String> recent = new ArrayDeque<String>();

    public RandomAltName() {
        this(new Random());
    }

    RandomAltName(Random random) {
        this.random = random;
    }

    /**
     * A new name that {@code taken} does not reject (e.g. names already in the Alt Manager), or null if none was
     * found after a number of attempts.
     */
    public String next(Predicate<String> taken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String name = candidate();
            if (!isValid(name) || (taken != null && taken.test(name))) continue;
            remember(baseOf(name));
            return name;
        }
        return null;
    }

    /** Minecraft rules: 3-16 characters, letters, digits and underscore. */
    public static boolean isValid(String name) {
        return name != null && name.length() >= 3 && name.length() <= MAX_LENGTH && name.matches("[A-Za-z0-9_]+");
    }

    String candidate() {
        String first = pickFresh(FIRST_NAMES);
        int style = this.random.nextInt(100);
        String name;
        if (style < 50) {
            name = first + digits(4);                                   // Pedro7319
        } else if (style < 62) {
            name = first + digits(2 + this.random.nextInt(2));          // Lucas284
        } else if (style < 72) {
            name = first.toLowerCase(Locale.ROOT) + "_" + digits(2 + this.random.nextInt(3));   // lucas_2846
        } else if (style < 82) {
            name = first + pick(SURNAMES) + digits(2);                  // JoaoSilva27
        } else if (style < 91) {
            name = first + (1996 + this.random.nextInt(16));            // Bruno2009
        } else {
            name = pickFresh(SHORT_NAMES) + digits(3 + this.random.nextInt(2));   // Rafa4821
        }
        return ascii(name);
    }

    /** Digits that do not look generated on purpose: no 0000, 1234 or 9876 runs and no leading zero. */
    String digits(int count) {
        while (true) {
            StringBuilder out = new StringBuilder(count);
            out.append((char) ('1' + this.random.nextInt(9)));
            for (int i = 1; i < count; i++) out.append((char) ('0' + this.random.nextInt(10)));
            String value = out.toString();
            if (!isObvious(value)) return value;
        }
    }

    static boolean isObvious(String digits) {
        if (digits.length() < 2) return false;
        boolean same = true;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < digits.length(); i++) {
            int previous = digits.charAt(i - 1);
            int current = digits.charAt(i);
            same &= current == previous;
            up &= current == previous + 1;
            down &= current == previous - 1;
        }
        return same || (digits.length() >= 3 && (up || down));
    }

    private String pickFresh(String[] pool) {
        for (int i = 0; i < 8; i++) {
            String value = pick(pool);
            if (!this.recent.contains(value)) return value;
        }
        return pick(pool);
    }

    private String pick(String[] pool) {
        return pool[this.random.nextInt(pool.length)];
    }

    private void remember(String base) {
        this.recent.remove(base);
        this.recent.addLast(base);
        while (this.recent.size() > RECENT_NAMES) this.recent.removeFirst();
    }

    /** The name part a candidate was built from ("lucas_2846" -> "Lucas"), for the recent-names memory. */
    private static String baseOf(String name) {
        String letters = name.replaceAll("[^A-Za-z].*$", "");
        for (String[] pool : new String[][]{FIRST_NAMES, SHORT_NAMES}) {
            for (String value : pool) {
                String plain = ascii(value);
                if (letters.regionMatches(true, 0, plain, 0, plain.length())) return value;
            }
        }
        return letters;
    }

    /** Usernames are ASCII only: strip accents ("Cau\u00e3" -> "Caua"). */
    static String ascii(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("[^A-Za-z0-9_]", "");
    }
}
