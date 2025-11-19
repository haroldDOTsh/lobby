package sh.harold.fulcrum.lobby.cosmetics.runtime;

/**
 * Simple immutable vector used inside async cosmetics logic to avoid Bukkit dependencies.
 */
public record Vector3d(double x, double y, double z) {
    public static final Vector3d ZERO = new Vector3d(0.0D, 0.0D, 0.0D);

    public Vector3d add(Vector3d other) {
        return new Vector3d(x + other.x, y + other.y, z + other.z);
    }

    public Vector3d subtract(Vector3d other) {
        return new Vector3d(x - other.x, y - other.y, z - other.z);
    }

    public Vector3d multiply(double scalar) {
        return new Vector3d(x * scalar, y * scalar, z * scalar);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }
}
