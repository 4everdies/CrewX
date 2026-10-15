package crewx.accountmanager.utils;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RandomAltNameTest {

    @Test
    public void everyNameIsAValidMinecraftUsername() {
        RandomAltName generator = new RandomAltName(new Random(1));
        for (int i = 0; i < 5000; i++) {
            String name = generator.next(null);
            assertNotNull(name);
            assertTrue(name, RandomAltName.isValid(name));
            assertTrue(name, name.length() <= 16);
            assertTrue(name, name.matches("[A-Za-z0-9_]+"));
        }
    }

    @Test
    public void namesAreNaturalNamePlusNumber() {
        RandomAltName generator = new RandomAltName(new Random(2));
        int withNumber = 0;
        for (int i = 0; i < 1000; i++) {
            String name = generator.next(null);
            assertTrue(name, Character.isLetter(name.charAt(0)));
            if (name.matches("[A-Za-z_]+[0-9]{2,4}")) withNumber++;
        }
        assertEquals(1000, withNumber);
    }

    @Test
    public void namesVaryAndDoNotRepeatBackToBack() {
        RandomAltName generator = new RandomAltName(new Random(3));
        Set<String> names = new HashSet<String>();
        Set<String> firstNames = new HashSet<String>();
        String previousBase = null;
        for (int i = 0; i < 200; i++) {
            String name = generator.next(null);
            names.add(name.toLowerCase(Locale.ROOT));
            String base = name.replaceAll("[^A-Za-z].*$", "").toLowerCase(Locale.ROOT);
            firstNames.add(base);
            assertFalse("same name twice in a row: " + name, base.equals(previousBase));
            previousBase = base;
        }
        assertTrue("only " + names.size() + " distinct names", names.size() >= 198);
        assertTrue("only " + firstNames.size() + " distinct first names", firstNames.size() >= 60);
    }

    @Test
    public void skipsNamesAlreadyInTheList() {
        final Set<String> existing = new HashSet<String>();
        RandomAltName generator = new RandomAltName(new Random(4));
        for (int i = 0; i < 300; i++) {
            String name = generator.next(candidate -> existing.contains(candidate.toLowerCase(Locale.ROOT)));
            assertNotNull(name);
            assertTrue("duplicate " + name, existing.add(name.toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    public void givesUpInsteadOfLoopingWhenEverythingIsTaken() {
        assertNull(new RandomAltName(new Random(5)).next(candidate -> true));
    }

    @Test
    public void avoidsObviousNumbers() {
        assertTrue(RandomAltName.isObvious("0000"));
        assertTrue(RandomAltName.isObvious("1111"));
        assertTrue(RandomAltName.isObvious("1234"));
        assertTrue(RandomAltName.isObvious("9876"));
        assertTrue(RandomAltName.isObvious("77"));
        assertFalse(RandomAltName.isObvious("4827"));
        assertFalse(RandomAltName.isObvious("12"));
        RandomAltName generator = new RandomAltName(new Random(6));
        for (int i = 0; i < 2000; i++) {
            String digits = generator.digits(4);
            assertFalse(digits, RandomAltName.isObvious(digits));
            assertTrue(digits, digits.charAt(0) != '0');
        }
    }

    @Test
    public void stripsAccents() {
        assertEquals("Caua", RandomAltName.ascii("Cauã"));
        assertEquals("Joao", RandomAltName.ascii("João"));
    }
}
