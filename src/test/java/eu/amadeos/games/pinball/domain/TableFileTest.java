package eu.amadeos.games.pinball.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableFileTest {
    @Test
    void aLineIsAKindAnIdNumbersNamesAndOptions() {
        TableSpec spec = TableFile.parse("test", """
                # a comment
                name Test_Table
                level 3
                art reef
                palette 000000 111111 222222 333333 444444 555555 666666 777777
                rules start=hole lock=toy lock-light=a,b
                wall@1 12 30 40 30   # a wall on the raised layer
                saucer hole 104 190 p6 out=95 kick=340
                mode Big_Shot shots=a,b* count=4 time=25 value=1000
                wizard Finale shots=a count=9 time=60 value=5 balls=4
                """);
        assertEquals("Test Table", spec.name());
        assertEquals(3, spec.level());
        assertEquals("reef", spec.art());
        assertEquals(0x777777, spec.palette()[7].getRGB() & 0xffffff);
        assertEquals("toy", spec.rule("lock", ""));
        assertEquals(List.of("a", "b"), spec.ruleList("lock-light"));
        Part wall = spec.parts("wall").getFirst();
        assertEquals(1, wall.layer());
        assertArrayEquals(new double[]{12, 30, 40, 30}, wall.nums());
        Part saucer = spec.parts("saucer").getFirst();
        assertEquals("hole", saucer.id());
        assertEquals(0, saucer.layer());
        assertEquals(340, saucer.option("kick", 0));
        assertEquals(8, saucer.line());
        TableSpec.ModeSpec mode = spec.modes().getFirst();
        assertEquals("Big Shot", mode.name());
        assertEquals(4, mode.count());
        assertTrue(mode.counts("b7"), "a shot ending in * counts every id it begins");
        assertTrue(mode.counts("a"));
        assertEquals(4, spec.wizard().balls());
    }

    @Test
    void anUnknownTableIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> TableFile.load("no-such-table"));
    }
}
