package sh.harold.fulcrum.lobby.cosmetics.examples;

import org.bukkit.Color;
import org.bukkit.Particle;
import sh.harold.fulcrum.lobby.cosmetics.CloakCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.runtime.ParticleInstruction;
import sh.harold.fulcrum.lobby.cosmetics.runtime.PlayerContext;
import sh.harold.fulcrum.lobby.cosmetics.runtime.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base for cloaks that render an 8x8 pattern anchored to the player's back.
 */
public abstract class PatternCloakCosmetic extends CloakCosmetic {
    private static final Vector3d UP = new Vector3d(0.0D, 1.0D, 0.0D);

    private final int[][] pattern;
    private final Map<Integer, Pixel> palette;
    private final double horizontalSpacing;
    private final double verticalSpacing;
    private final double depthOffset;
    private final double anchorHeight;

    protected PatternCloakCosmetic(
            CosmeticDescriptor descriptor,
            int[][] pattern,
            Map<Integer, Pixel> palette,
            double widthBlocks,
            double heightBlocks,
            double depthOffset,
            double anchorHeight
    ) {
        super(descriptor);
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(palette, "palette");
        if (pattern.length == 0 || pattern[0].length == 0) {
            throw new IllegalArgumentException("pattern must have at least one row and column");
        }
        this.pattern = pattern;
        this.palette = palette;
        int width = pattern[0].length;
        int height = pattern.length;
        this.horizontalSpacing = width <= 1 ? 0.0D : widthBlocks / (width - 1);
        this.verticalSpacing = height <= 1 ? 0.0D : heightBlocks / (height - 1);
        this.depthOffset = depthOffset;
        this.anchorHeight = anchorHeight;
    }

    @Override
    public List<ParticleInstruction> tick(PlayerContext ctx) {
        int height = pattern.length;
        int width = pattern[0].length;
        double halfWidth = (width - 1) / 2.0D;
        double halfHeight = (height - 1) / 2.0D;
        List<ParticleInstruction> instructions = new ArrayList<>();

        double yawRadians = Math.toRadians(ctx.yaw());
        Vector3d forward = new Vector3d(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).normalize();
        Vector3d back = forward.multiply(-1.0D).normalize();
        Vector3d right = new Vector3d(-back.z(), 0.0D, back.x()).normalize();
        Vector3d anchor = ctx.position()
                .add(back.multiply(depthOffset))
                .add(new Vector3d(0.0D, anchorHeight, 0.0D));

        double time = ctx.epochMillis() * 0.004D;
        double flutter = Math.sin(time) * 0.05D;

        for (int row = 0; row < height; row++) {
            double verticalOffset = (halfHeight - row) * verticalSpacing;
            for (int column = 0; column < width; column++) {
                int value = pattern[row][column];
                Pixel pixel = palette.get(value);
                if (pixel == null) {
                    continue;
                }
                double horizontalOffset = (column - halfWidth) * horizontalSpacing;
                Vector3d offset = right.multiply(horizontalOffset)
                        .add(UP.multiply(verticalOffset))
                        .add(back.multiply(flutter));
                Vector3d position = anchor.add(offset).add(pixel.offset());
                instructions.add(new ParticleInstruction(
                        pixel.particle(),
                        ctx.worldId(),
                        position,
                        Vector3d.ZERO,
                        pixel.count(),
                        pixel.extra(),
                        pixel.data(),
                        pixel.force()
                ));
            }
        }
        return instructions;
    }

    protected record Pixel(
            Particle particle,
            Vector3d offset,
            int count,
            double extra,
            Object data,
            boolean force
    ) {
        public Pixel(Particle particle, Vector3d offset, int count, double extra, Object data, boolean force) {
            this.particle = Objects.requireNonNull(particle, "particle");
            this.offset = offset == null ? Vector3d.ZERO : offset;
            this.count = count <= 0 ? 1 : count;
            this.extra = extra;
            this.data = data;
            this.force = force;
        }

        public static Pixel of(Particle particle) {
            return new Pixel(particle, Vector3d.ZERO, 1, 0.0D, null, false);
        }

        public static Pixel of(Particle particle, Vector3d offset, int count, double extra, Object data, boolean force) {
            return new Pixel(particle, offset, count, extra, data, force);
        }

        public static Pixel ofDust(Color color, float size) {
            return new Pixel(Particle.DUST, Vector3d.ZERO, 1, 0.0D, new Particle.DustOptions(color, size), false);
        }
    }
}
