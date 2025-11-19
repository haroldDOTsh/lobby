package sh.harold.fulcrum.lobby.cosmetics.registry;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.lobby.cosmetics.Cosmetic;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticMetadata;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticKeys;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers cosmetics via {@link CosmeticMetadata} annotations by scanning the plugin jar.
 */
public final class CosmeticRegistry {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Logger logger;
    private final ClassLoader classLoader;

    public CosmeticRegistry(JavaPlugin plugin, Logger logger) {
        this.logger = logger;
        this.classLoader = plugin.getClass().getClassLoader();
        scan(resolvePluginFile(plugin));
    }

    public Collection<CosmeticDescriptor> descriptors() {
        return Collections.unmodifiableCollection(entries.values().stream()
                .map(Entry::descriptor)
                .toList());
    }

    public Optional<CosmeticDescriptor> descriptor(String id) {
        String normalized = CosmeticKeys.normalizeId(id);
        Entry entry = entries.get(normalized);
        return entry == null ? Optional.empty() : Optional.of(entry.descriptor());
    }

    public Optional<Cosmetic> instantiate(String id) {
        String normalized = CosmeticKeys.normalizeId(id);
        Entry entry = entries.get(normalized);
        if (entry == null) {
            return Optional.empty();
        }
        return instantiate(entry);
    }

    public Optional<Cosmetic> instantiateFromFlatKey(String flatKey) {
        String normalized = CosmeticKeys.normalizeId(flatKey);
        Entry entry = entries.get(normalized);
        if (entry != null) {
            return instantiate(entry);
        }
        return CosmeticKeys.setIdFromPieceKey(normalized)
                .map(entries::get)
                .flatMap(this::instantiate);
    }

    private Optional<Cosmetic> instantiate(Entry entry) {
        try {
            return Optional.of(entry.constructor().newInstance(entry.descriptor()));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to instantiate cosmetic " + entry.descriptor().id(), ex);
            }
            return Optional.empty();
        }
    }

    private void scan(File pluginFile) {
        if (pluginFile == null || !pluginFile.isFile()) {
            if (logger != null) {
                logger.warning("Plugin file unavailable; cosmetics registry cannot scan.");
            }
            return;
        }
        long scanned = 0L;
        long registered = 0L;
        try (JarFile jarFile = new JarFile(pluginFile)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                scanned++;
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                if (!className.startsWith("sh.harold.fulcrum")) {
                    continue;
                }
                tryRegister(className);
            }
            registered = this.entries.size();
        } catch (IOException exception) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Unable to scan plugin jar for cosmetics", exception);
            }
        }
        if (logger != null) {
            logger.info("Cosmetic registry scanned " + scanned + " classes, registered " + registered + " cosmetics");
        }
    }

    private File resolvePluginFile(JavaPlugin plugin) {
        CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
        if (source == null) {
            return null;
        }
        try {
            return new File(source.getLocation().toURI());
        } catch (URISyntaxException exception) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Unable to resolve plugin source for cosmetics scanning", exception);
            }
            return null;
        }
    }

    private void tryRegister(String className) {
        try {
            Class<?> resolved = Class.forName(className, false, classLoader);
            CosmeticMetadata metadata = resolved.getAnnotation(CosmeticMetadata.class);
            if (metadata == null) {
                return;
            }
            if (!Cosmetic.class.isAssignableFrom(resolved)) {
                if (logger != null) {
                    logger.warning(() -> "Class " + className + " has @CosmeticMetadata but does not implement Cosmetic");
                }
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Cosmetic> cosmeticType = (Class<? extends Cosmetic>) resolved;
            register(cosmeticType, metadata);
        } catch (ClassNotFoundException exception) {
            if (logger != null) {
                logger.log(Level.WARNING, "Unable to resolve cosmetic class " + className, exception);
            }
        }
    }

    private void register(Class<? extends Cosmetic> type, CosmeticMetadata metadata) {
        CosmeticDescriptor descriptor = CosmeticDescriptor.fromMetadata(metadata);
        Constructor<? extends Cosmetic> constructor;
        try {
            constructor = type.getDeclaredConstructor(CosmeticDescriptor.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException exception) {
            if (logger != null) {
                logger.warning(() -> "Cosmetic " + type.getName()
                        + " is missing a constructor accepting CosmeticDescriptor");
            }
            return;
        }

        String id = descriptor.id().toLowerCase(Locale.ROOT);
        Entry existing = entries.putIfAbsent(id, new Entry(descriptor, constructor));
        if (existing != null && logger != null) {
            logger.warning("Duplicate cosmetic id '" + id + "' between "
                    + type.getName() + " and " + existing.constructor().getDeclaringClass().getName());
        }
    }

    private record Entry(CosmeticDescriptor descriptor, Constructor<? extends Cosmetic> constructor) {
    }
}
