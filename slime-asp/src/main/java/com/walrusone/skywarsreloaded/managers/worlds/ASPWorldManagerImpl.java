package com.walrusone.skywarsreloaded.managers.worlds;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.walrusone.skywarsreloaded.SkyWarsReloaded;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.util.logging.Level;

@SuppressWarnings({"CallToPrintStackTrace", "unused"})
public class ASPWorldManagerImpl implements ASPWorldManager {

    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final SlimeLoader loader;

    public ASPWorldManagerImpl() {
        // Loader instances are not provided by the API — instantiate the file loader directly.
        // The source name from config maps to loader type; default to file-based loader.
        String source = SkyWarsReloaded.getCfg().getSlimeWorldManagerSource();
        this.loader = createLoader(source);
    }

    private SlimeLoader createLoader(String source) {
        // ASP loaders are not on the classpath here (they must be shaded separately).
        // We use reflection to instantiate the FileLoader so the build doesn't fail when
        // the loader artifact is absent, and fall back to null gracefully at runtime.
        try {
            String loaderClass;
            switch (source.toLowerCase()) {
                case "mongodb":
                case "mongo":
                    loaderClass = "com.infernalsuite.asp.loaders.mongodb.MongoDbLoader";
                    break;
                case "mysql":
                    loaderClass = "com.infernalsuite.asp.loaders.mysql.MysqlLoader";
                    break;
                case "redis":
                    loaderClass = "com.infernalsuite.asp.loaders.redis.RedisLoader";
                    break;
                default:
                    loaderClass = "com.infernalsuite.asp.loaders.file.FileLoader";
                    break;
            }
            Class<?> clazz = Class.forName(loaderClass);
            if (source.equalsIgnoreCase("file") || source.equalsIgnoreCase("default")) {
                return (SlimeLoader) clazz.getConstructor(String.class).newInstance("slime_worlds");
            }
            return (SlimeLoader) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            SkyWarsReloaded.get().getLogger().log(Level.SEVERE,
                    "Failed to instantiate ASP SlimeLoader for source '" + source + "'. Falling back to file loader.", e);
            try {
                Class<?> clazz = Class.forName("com.infernalsuite.asp.loaders.file.FileLoader");
                return (SlimeLoader) clazz.getConstructor(String.class).newInstance("slime_worlds");
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public World createEmptyWorld(String name, World.Environment environment) {
        if (loader == null) return null;

        SlimePropertyMap propertyMap = new SlimePropertyMap();
        propertyMap.setValue(SlimeProperties.SPAWN_X, 0);
        propertyMap.setValue(SlimeProperties.SPAWN_Y, 64);
        propertyMap.setValue(SlimeProperties.SPAWN_Z, 0);
        propertyMap.setValue(SlimeProperties.ENVIRONMENT, environment.name().toLowerCase());
        propertyMap.setValue(SlimeProperties.DIFFICULTY, "normal");

        try {
            SlimeWorld slimeWorld = asp.createEmptyWorld(name, false, propertyMap, loader);
            SlimeWorldInstance instance = asp.loadWorld(slimeWorld, true);

            World bukkitWorld = instance.getBukkitWorld();
            if (bukkitWorld == null) return null;

            new Location(bukkitWorld, 0, 61, 0).getBlock().setType(Material.BEDROCK);
            applyWorldSettings(bukkitWorld);
            return bukkitWorld;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean loadWorld(String worldName, World.Environment environment, boolean readOnly) {
        if (loader == null) return false;

        if (SkyWarsReloaded.getCfg().debugEnabled()) {
            SkyWarsReloaded.get().getLogger().info(getClass().getName() + "#loadWorld: " + worldName);
        }

        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            SkyWarsReloaded.get().getServer().unloadWorld(existing, false);
        }

        try {
            SlimePropertyMap propertyMap = new SlimePropertyMap();
            propertyMap.setValue(SlimeProperties.ENVIRONMENT, environment.name().toLowerCase());

            SlimeWorld slimeWorld = asp.readWorld(loader, worldName, readOnly, propertyMap);
            SlimeWorldInstance instance = asp.loadWorld(slimeWorld, false);

            World world = instance.getBukkitWorld();
            if (world == null) {
                Bukkit.getLogger().log(Level.SEVERE, "World is null after loading: " + worldName);
                return false;
            }

            applyWorldSettings(world);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void unloadWorld(String worldName, boolean shouldSave) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            SkyWarsReloaded.get().getLogger().severe("World " + worldName + " is not loaded but unload was attempted!");
            return;
        }

        try {
            if (shouldSave) {
                SlimeWorldInstance instance = asp.getLoadedWorld(worldName);
                if (instance != null) {
                    asp.saveWorld(instance);
                }
            }
            Bukkit.unloadWorld(world, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public WorldManagerType getType() {
        return WorldManagerType.ASWM;
    }

    @Override
    public void copyWorld(File source, File target) {
        // Not applicable for slime worlds
    }

    @Override
    public void deleteWorld(String name, boolean removeFile) {
        unloadWorld(name, false);
        if (removeFile && loader != null) {
            try {
                loader.deleteWorld(name);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "Failed to delete slime world: " + name);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deleteWorld(File file) {
        // No-op: slime worlds are not file-based in this context
    }

    private void applyWorldSettings(World world) {
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        world.setSpawnFlags(true, true);
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(false);

        SkyWarsReloaded.getNMS().setGameRule(world, "doMobSpawning", "false");
        SkyWarsReloaded.getNMS().setGameRule(world, "mobGriefing", "false");
        SkyWarsReloaded.getNMS().setGameRule(world, "doFireTick", "false");
        SkyWarsReloaded.getNMS().setGameRule(world, "showDeathMessages", "false");
        SkyWarsReloaded.getNMS().setGameRule(world, "announceAdvancements", "false");
        SkyWarsReloaded.getNMS().setGameRule(world, "doDaylightCycle", "false");
    }
}
