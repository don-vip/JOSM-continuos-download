package org.openstreetmap.josm.plugins.continuosDownload;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link Interval}
 */
class IntervalTest {

    @Test
    void testContains() {
        assertTrue(new Interval(0, 1).contains(0));
        assertFalse(new Interval(0, 1).contains(1));
        assertTrue(new Interval(0, 2).contains(1));
        assertFalse(new Interval(0, 1).contains(-1));
        assertFalse(new Interval(0, 1).contains(2));
    }
    @Test
    void testValid() {
        assertTrue(new Interval(0, 1).valid());
        assertFalse(new Interval(1, 0).valid());
        assertFalse(new Interval(0, 0).valid());
        assertFalse(new Interval(1, 1).valid());
    }

    @Test
    void testIntersects() {
        assertTrue(new Interval(0, 2).intersects(new Interval(1, 3)));
        assertFalse(new Interval(0, 1).intersects(new Interval(1, 2)));
        assertFalse(new Interval(1, 0).intersects(new Interval(0, 2)));
        assertFalse(new Interval(0, 2).intersects(new Interval(3, 1)));
    }

    @Test
    void testUnion() {
        assertEquals(new Interval(0, 2),
                new Interval(0, 1).union(new Interval(1, 2)));
        assertEquals(new Interval(0, 3),
                new Interval(0, 1).union(new Interval(2, 3)));
        assertEquals(new Interval(0, 2),
                new Interval(0, 2).union(new Interval(0, 2)));
        assertEquals(new Interval(0, 1),
                new Interval(0, 1).union(new Interval(2, 1)));
    }

    @Test
    void testIntersection() {
        assertFalse((new Interval(0, 1).intersection(new Interval(1, 2))).valid());
        assertEquals(new Interval(0, 1),
                new Interval(-1, 1).intersection(new Interval(0, 2)));
    }
}
