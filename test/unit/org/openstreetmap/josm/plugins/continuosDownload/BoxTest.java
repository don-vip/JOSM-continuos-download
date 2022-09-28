package org.openstreetmap.josm.plugins.continuosDownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link Box}
 */
class BoxTest {

    @Test
    void testValid() {
        assertTrue(new Box(0, 0, 1, 1).valid());
        assertFalse(new Box(1, 1, 0, 0).valid());
    }

    @Test
    void testIntersects() {
        assertTrue(new Box(0, 0, 2, 2).intersects(new Box(1, 1, 3, 3)));
        assertFalse(new Box(0, 0, 1, 1).intersects(new Box(1, 1, 2, 2)));
        assertFalse(new Box(0, 0, 1, 1).intersects(new Box(0, 1, 1, 2)));
        assertFalse(new Box(0, 0, 1, 1).intersects(new Box(1, 0, 2, 1)));
    }

    @Test
    void testEquals() {
        assertEquals(new Box(1, 1, 2, 2), new Box(1, 1, 2, 2));
    }

    @Test
    void testIntersection() {
        assertEquals(new Box(1, 1, 2, 2),
                new Box(0, 0, 2, 2).intersection(new Box(1, 1, 3, 3)));
        assertFalse((new Box(0, 0, 1, 1).intersection(new Box(1, 1, 2, 2))).valid());
        assertFalse((new Box(0, 0, 1, 1).intersection(new Box(0, 1, 1, 2))).valid());
    }

    @Test
    void testUnion() {
        assertEquals(new Box(0, 0, 2, 2),
                new Box(0, 0, 1, 1).union(new Box(1, 1, 2, 2)));
    }

    @Test
    void testInverse() {
        for (int i = 0; i < 100; i++) {
            Box x = random_box();
            for (Box y : x.inverse()) {
                assertFalse(x.intersects(y));
            }
        }
    }

    @Test
    void testSubtract() {
        for (int i = 0; i < 100; i++) {
            Box x = random_box();
            Box y = random_box();
            for (Box b : x.substract(y)) {
                assertFalse(b.intersects(y));
                assertTrue(b.intersects(x));
            }
        }
    }

    @Test
    void testSubtractAll() {
        for (int i = 0; i < 10; i++) {
            Box x = random_box();
            Collection<Box> array = new ArrayList<>(10);
            for (int j = 0; j < 10; j++) {
                array.add(random_box());
            }
            Collection<Box> subtraction = x.subtract_all(array);
            for (Box b : subtraction) {
                for (Box a : array) {
                    assertFalse(a.intersects(b));
                }
                for (Box a : subtraction) {
                    assertFalse(a != b && a.intersects(b));
                }
                assertTrue(b.intersects(x));
            }
        }
    }

    private Box random_box() {
        long minx = (long) (Math.random() * 2000 - 1000);
        long miny = (long) (Math.random() * 2000 - 1000);
        long maxx = (long) (Math.random() * 2000 - 1000);
        long maxy = (long) (Math.random() * 2000 - 1000);

        if (minx > maxx) {
            long t = minx;
            minx = maxx;
            maxx = t;
        }
        if (miny > maxy) {
            long t = miny;
            miny = maxy;
            maxy = t;
        }
        return new Box(minx, miny, maxx, maxy);
    }
}
