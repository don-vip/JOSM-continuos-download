package org.openstreetmap.josm.plugins.continuosDownload;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainApplication;

class BoxStrategyTest {

    @Test
    void test() {
        ArrayList<Box> set = new ArrayList<>();
        set.add(new Box(0, 0, 1, 1));
        set.add(new Box(1, 1, 2, 2));

        Collection<Box> r = BoxStrategy.optimalPart(3, set);

        assertEquals(2, r.size());
        for (Box b : r) {
            assertEquals(1, b.size(), 0.000000001);
        }
    }

    @Test
    void test2() {
        Collection<Bounds> existing = new ArrayList<>();
        existing.add(new Bounds(0, 0, 1, 1));

        BoxStrategy strat = new BoxStrategy();

        Collection<Bounds> r = strat.getBoxes(new Bounds(0, -1, 1, 2), existing, 3);

        assertEquals(2, r.size());
        for (Bounds b : r) {
            assertEquals(1, b.getArea(), 0.000000001);
        }
    }

    @Test
    void testStress() {
        /*
         * This should be the worst case scenario. 25 boxes of 2x2 and a smaller
         * box some distance away.
         */
        ArrayList<Box> set = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                set.add(new Box(i * 3, j * 3, i * 3 + 2, j * 3 + 2));
        set.add(new Box(-10, -10, -9, -9));

        long t0 = System.currentTimeMillis();
        /*Collection<Box> r =*/ BoxStrategy.optimalPart(4, set);

        assertTrue(System.currentTimeMillis() < t0 + 4000);
    }

    /**
     * Non-regression test for #22351: NPE: Cannot invoke "java.util.Collection.isEmpty()" because "existing" is null
     */
    @Test
    void testNonRegression22351() {
        BoxStrategy boxStrategy = new BoxStrategy();
        // We cannot be displaying a map view. This is largely a sanity check.
        assertFalse(MainApplication.isDisplayingMapView());
        assertDoesNotThrow(() -> boxStrategy.fetch(new Bounds(0, 0, 1, 1)));
    }
}
