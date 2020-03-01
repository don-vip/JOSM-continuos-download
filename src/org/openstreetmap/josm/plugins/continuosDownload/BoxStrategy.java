// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.plugins.continuosDownload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

public class BoxStrategy extends AbstractDownloadStrategy {

    @Override
    public Collection<Bounds> getBoxes(Bounds bbox, Collection<Bounds> present, int maxBoxes) {
        Collection<Box> existing = Box.merge(fromBounds(present));
        Collection<Box> bits = Box.merge(fromBounds(bbox).subtract_all(existing));
        Collection<Box> toFetch = optimalPart(maxBoxes, bits);
        return toBounds(Box.merge(toFetch));
    }

    /**
     * find the optimal partition - the one which requests the smallest amount
     * of extra space - given the set p of partitions
     */
    public static Collection<Box> optimalPart(int maxParts, Collection<Box> set) {
        /*
         * BUG: This code have no safeguards against timing out or running out
         * of memory. It did not happen during testing, but that is no guaranty
         * it will not happen.
         */
        ArrayList<Box> list = new ArrayList<>(set);
        // Sort the set from largest to smalest, there is a better chance of
        // getting good pactitions if you start with the biggest boxes because
        // the smaller boxes have little impact on the overall score.
		Collections.sort(list, Comparator.comparingDouble(Box::size).reversed());

        PriorityQueue<Partition> q = new PriorityQueue<>();
        q.add(new Partition());

        // Find the best partition this far and add another box to it until the
        // best partition is a partition of the complete set.
        while (!q.isEmpty()) {
            Partition a = q.remove();
            if (a.size == list.size()) {
                return a.box;
            }
            
            Box next = list.get(a.size);
            
            // Add a new box to every part in the partition and put those in the
            // queue
            for (int i = 0; i < maxParts && i <= a.box.size(); i++) {
                q.add(a.add(next, i));
            }
        }

        return null;
    }

    public static class Partition implements Comparable<Partition> {
        ArrayList<Box> box; // The merged boxes
        int size; // How many boxes have we merged this far
        double enclosingArea; // The area of the boxes we have added

        private Partition(ArrayList<Box> n, int i, double area) {
            box = n;
            size = i;
            enclosingArea = area;
        }

        /**
         * Constructs a new {@code Partition}.
         */
        public Partition() {
            this(new ArrayList<Box>(), 0, 0);
        }

        // Create a new partition with an extra box in the ith place
        public Partition add(Box next, int i) {
            @SuppressWarnings("unchecked")
            ArrayList<Box> n = (ArrayList<Box>) box.clone();
            if (n.size() <= i) {
                n.add(next);
            } else {
                n.set(i, n.get(i).union(next));
            }
            return new Partition(n, size + 1, enclosingArea + next.size());
        }

        @Override
        public int compareTo(Partition other) {
            double a = area() - enclosingArea;
            double b = other.area() - other.enclosingArea;

            // Get the partition that downloads the least amount of extra area
            if (a > b)
                return 1;
            if (a < b)
                return -1;

            // Try to get to the end faster by getting the partition over the
            // most boxes
            if (size > other.size)
                return -1;
            if (size < other.size)
                return 1;

            // Prefer a partition that downloads fewest boxes
            if (box.size() > other.box.size())
                return 1;
            if (box.size() < other.box.size())
                return -1;
            return 0;
        }

        double area() {
            double r = 0;

            for (Box b : box) {
                r += b.size();
            }

            return r;
        }
    }

    /*
     * The next part is conversion between double bboxes and fpi bboxes. Sending
     * doubles to the server and back through multiple conversions and parsers
     * is less accurate. This makes it hard to detect if two areas are adjacent.
     * Converting to fpi makes computation faster and more accurate.
     */

    /**
     * Converts a double to a fixed precision integer with 7 digits
     */
    private static long toFpi(double n) {
        return (long) (n * 10000000);
    }

    /**
     * Converts a fixed precision integer to a double
     */
    private static double fromFpi(long n) {
        return (n / 10000000.0);
    }

    /**
     * Converts from bounds used in josm to boxes used here
     */
    public static Box fromBounds(Bounds bbox) {
        LatLon min = bbox.getMin(), max = bbox.getMax();
        return new Box(toFpi(min.getX()), toFpi(min.getY()), toFpi(max.getX()),
                toFpi(max.getY()));
    }

    /**
     * Converts from boxes to bounds
     */
    public static Bounds toBounds(Box bbox) {
        return new Bounds(fromFpi(bbox.y.min), fromFpi(bbox.x.min),
                fromFpi(bbox.y.max), fromFpi(bbox.x.max));
    }

    /**
     * Converts a set of boxes from bounds used in josm to boxes used here
     */
    public static Collection<Box> fromBounds(Collection<Bounds> bbox) {
        ArrayList<Box> r = new ArrayList<>(bbox.size());
        for (Bounds box : bbox) {
            r.add(fromBounds(box));
        }
        return r;
    }

    /**
     * Converts a set of boxes from boxes to bounds
     */
    public static Collection<Bounds> toBounds(Collection<Box> bbox) {
        ArrayList<Bounds> r = new ArrayList<>(bbox.size());
        for (Box box : bbox) {
            r.add(toBounds(box));
        }
        return r;
    }
}
