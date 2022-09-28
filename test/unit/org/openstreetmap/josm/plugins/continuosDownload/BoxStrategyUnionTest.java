package org.openstreetmap.josm.plugins.continuosDownload;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.continuosDownload.BoxStrategy.Partition;

class BoxStrategyUnionTest {

    @Test
    void test() {
        Partition a = new Partition();
        a = a.add(new Box(0, 0, 1, 1), 0);
        assertEquals(1, a.box.size());
        assertEquals(1, a.area(), 0.0000001);
        assertEquals(1, a.enclosingArea, 0.0000001);

        Partition b = a.add(new Box(1, 1, 2, 2), 0);
        assertEquals(1, b.box.size());
        assertEquals(4, b.area(), 0.0000001);
        assertEquals(2, b.enclosingArea, 0.0000001);

        Partition c = a.add(new Box(1, 1, 2, 2), 1);
        assertEquals(2, c.box.size());
        assertEquals(2, c.area(), 0.0000001);
        assertEquals(2, c.enclosingArea, 0.0000001);

        assertTrue(b.compareTo(c) > 0);
    }

}
