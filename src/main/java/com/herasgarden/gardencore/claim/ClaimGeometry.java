package com.herasgarden.gardencore.claim;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimGeometry {
    private final UUID worldId;
    private final String worldName;
    private final List<ClaimPoint> vertices;
    private final int minY;
    private final int maxY;
    private final boolean fullHeight;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    public ClaimGeometry(UUID worldId, String worldName, List<ClaimPoint> vertices,
                         int minY, int maxY, boolean fullHeight) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("A claim requires at least 3 polygon vertices.");
        }
        this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.fullHeight = fullHeight;

        int localMinX = Integer.MAX_VALUE;
        int localMaxX = Integer.MIN_VALUE;
        int localMinZ = Integer.MAX_VALUE;
        int localMaxZ = Integer.MIN_VALUE;
        for (ClaimPoint point : vertices) {
            localMinX = Math.min(localMinX, point.x());
            localMaxX = Math.max(localMaxX, point.x());
            localMinZ = Math.min(localMinZ, point.z());
            localMaxZ = Math.max(localMaxZ, point.z());
        }
        this.minX = localMinX;
        this.maxX = localMaxX;
        this.minZ = localMinZ;
        this.maxZ = localMaxZ;
    }

    public static ClaimGeometry rectangle(World world, ClaimPoint first, ClaimPoint second,
                                          int minY, int maxY, boolean fullHeight) {
        int minX = Math.min(first.x(), second.x());
        int maxX = Math.max(first.x(), second.x());
        int minZ = Math.min(first.z(), second.z());
        int maxZ = Math.max(first.z(), second.z());
        List<ClaimPoint> points = List.of(
                new ClaimPoint(minX, minZ),
                new ClaimPoint(maxX, minZ),
                new ClaimPoint(maxX, maxZ),
                new ClaimPoint(minX, maxZ)
        );
        return new ClaimGeometry(world.getUID(), world.getName(), points, minY, maxY, fullHeight);
    }

    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public List<ClaimPoint> vertices() { return vertices; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public boolean fullHeight() { return fullHeight; }
    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }

    public int width() {
        return maxX - minX + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public long blockAreaEstimate() {
        if (isAxisAlignedRectangle()) {
            return (long) width() * length();
        }

        long twiceArea = 0L;
        long boundaryPoints = 0L;
        for (int i = 0; i < vertices.size(); i++) {
            ClaimPoint a = vertices.get(i);
            ClaimPoint b = vertices.get((i + 1) % vertices.size());
            twiceArea += (long) a.x() * b.z() - (long) b.x() * a.z();
            boundaryPoints += gcd(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
        }
        twiceArea = Math.abs(twiceArea);

        // Pick's theorem. We claim lattice blocks whose X/Z coordinates are inside or on the polygon.
        // I + B = A + B/2 + 1. Working with doubled values avoids floating point drift.
        return Math.max(1L, (twiceArea + boundaryPoints) / 2L + 1L);
    }

    public long blockVolumeEstimate() {
        return blockAreaEstimate() * (long) height();
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
            return false;
        }
        int y = location.getBlockY();
        return y >= minY && y <= maxY && contains2D(location.getBlockX(), location.getBlockZ());
    }

    public boolean contains2D(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }

        for (int i = 0; i < vertices.size(); i++) {
            ClaimPoint a = vertices.get(i);
            ClaimPoint b = vertices.get((i + 1) % vertices.size());
            if (pointOnSegment(x, z, a, b)) {
                return true;
            }
        }

        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            ClaimPoint a = vertices.get(i);
            ClaimPoint b = vertices.get(j);
            boolean crosses = ((a.z() > z) != (b.z() > z))
                    && (x < (double) (b.x() - a.x()) * (z - a.z()) / (double) (b.z() - a.z()) + a.x());
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean overlaps3D(ClaimGeometry other) {
        if (!worldId.equals(other.worldId)) {
            return false;
        }
        if (maxY < other.minY || other.maxY < minY) {
            return false;
        }
        return overlaps2D(other);
    }

    public boolean overlaps2D(ClaimGeometry other) {
        if (!worldId.equals(other.worldId)) {
            return false;
        }
        if (maxX < other.minX || other.maxX < minX || maxZ < other.minZ || other.maxZ < minZ) {
            return false;
        }

        for (int i = 0; i < vertices.size(); i++) {
            ClaimPoint a1 = vertices.get(i);
            ClaimPoint a2 = vertices.get((i + 1) % vertices.size());
            for (int j = 0; j < other.vertices.size(); j++) {
                ClaimPoint b1 = other.vertices.get(j);
                ClaimPoint b2 = other.vertices.get((j + 1) % other.vertices.size());
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }

        ClaimPoint ours = vertices.get(0);
        ClaimPoint theirs = other.vertices.get(0);
        return other.contains2D(ours.x(), ours.z()) || contains2D(theirs.x(), theirs.z());
    }

    public boolean containsGeometry(ClaimGeometry other) {
        if (!worldId.equals(other.worldId) || other.minY < minY || other.maxY > maxY) {
            return false;
        }
        for (ClaimPoint point : other.vertices) {
            if (!contains2D(point.x(), point.z())) {
                return false;
            }
        }
        return true;
    }

    public boolean isAxisAlignedRectangle() {
        if (vertices.size() != 4) {
            return false;
        }
        for (ClaimPoint point : vertices) {
            boolean validX = point.x() == minX || point.x() == maxX;
            boolean validZ = point.z() == minZ || point.z() == maxZ;
            if (!validX || !validZ) {
                return false;
            }
        }
        return true;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private static boolean pointOnSegment(int x, int z, ClaimPoint a, ClaimPoint b) {
        long cross = (long) (x - a.x()) * (b.z() - a.z()) - (long) (z - a.z()) * (b.x() - a.x());
        if (cross != 0) {
            return false;
        }
        return x >= Math.min(a.x(), b.x()) && x <= Math.max(a.x(), b.x())
                && z >= Math.min(a.z(), b.z()) && z <= Math.max(a.z(), b.z());
    }

    private static boolean segmentsIntersect(ClaimPoint a, ClaimPoint b, ClaimPoint c, ClaimPoint d) {
        long o1 = orientation(a, b, c);
        long o2 = orientation(a, b, d);
        long o3 = orientation(c, d, a);
        long o4 = orientation(c, d, b);

        if ((o1 > 0 && o2 < 0 || o1 < 0 && o2 > 0)
                && (o3 > 0 && o4 < 0 || o3 < 0 && o4 > 0)) {
            return true;
        }

        return o1 == 0 && pointOnSegment(c.x(), c.z(), a, b)
                || o2 == 0 && pointOnSegment(d.x(), d.z(), a, b)
                || o3 == 0 && pointOnSegment(a.x(), a.z(), c, d)
                || o4 == 0 && pointOnSegment(b.x(), b.z(), c, d);
    }

    private static long orientation(ClaimPoint a, ClaimPoint b, ClaimPoint c) {
        return (long) (b.x() - a.x()) * (c.z() - a.z())
                - (long) (b.z() - a.z()) * (c.x() - a.x());
    }
}
