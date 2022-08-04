package com.lagradost.cloudstream3.plugins;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import android.os.Environment;
import com.google.gson.Gson;

import dalvik.system.PathClassLoader;


public class PluginManager {
    public static final String PLUGINS_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Cloudstream3/plugins";

    public static final Map<String, Plugin> plugins = new LinkedHashMap<>();
    public static final Map<PathClassLoader, Plugin> classLoaders = new HashMap<>();
    public static final Map<File, Object> failedToLoad = new LinkedHashMap<>();

    public static boolean loadedPlugins = false;

    private static final Gson gson = new Gson();

    public static void loadAllPlugins(Context context) {
        File dir = new File(PLUGINS_PATH);
        if (!dir.exists()) {
            boolean res = dir.mkdirs();
            if (!res) {
                //logger.error("Failed to create directories!", null);
                return;
            }
        }

        File[] sortedPlugins = dir.listFiles();
        // Always sort plugins alphabetically for reproducible results
        Arrays.sort(sortedPlugins, Comparator.comparing(File::getName));

        for (File f : sortedPlugins) {
            String name = f.getName();
            if (name.endsWith(".zip")) {
                PluginManager.loadPlugin(context, f);
            } else if (!name.equals("oat")) { // Some roms create this
                if (f.isDirectory()) {
                    //Utils.showToast(String.format("Found directory %s in your plugins folder. DO NOT EXTRACT PLUGIN ZIPS!", name), true);
                } else if (name.equals("classes.dex") || name.endsWith(".json")) {
                    //Utils.showToast(String.format("Found extracted plugin file %s in your plugins folder. DO NOT EXTRACT PLUGIN ZIPS!", name), true);
                }
                //rmrf(f);
            }
        }
        loadedPlugins = true;
        //if (!PluginManager.failedToLoad.isEmpty())
            //Utils.showToast("Some plugins failed to load.");
    }

    @SuppressWarnings({ "JavaReflectionMemberAccess", "unchecked" })
    public static void loadPlugin(Context context, File file) {
        String fileName = file.getName().replace(".zip", "");
        //logger.info("Loading plugin: " + fileName);
        try {
            PathClassLoader loader = new PathClassLoader(file.getAbsolutePath(), context.getClassLoader());

            Plugin.Manifest manifest;

            try (InputStream stream = loader.getResourceAsStream("manifest.json")) {
                if (stream == null) {
                    failedToLoad.put(file, "No manifest found");
                    //logger.error("Failed to load plugin " + fileName + ": No manifest found", null);
                    return;
                }

                try (InputStreamReader reader = new InputStreamReader(stream)) {
                    manifest = gson.fromJson(reader, Plugin.Manifest.class);
                }
            }

            String name = manifest.name;

            Class pluginClass = (Class<? extends Plugin>) loader.loadClass(manifest.pluginClassName);

            Plugin pluginInstance = (Plugin)pluginClass.newInstance();
            if (plugins.containsKey(name)) {
                //logger.error("Plugin with name " + name + " already exists", null);
                return;
            }

            pluginInstance.__filename = fileName;
            if (pluginInstance.needsResources) {
                // based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                AssetManager assets = AssetManager.class.newInstance();
                Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
                addAssetPath.invoke(assets, file.getAbsolutePath());
                pluginInstance.resources = new Resources(assets, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());
            }
            plugins.put(name, pluginInstance);
            classLoaders.put(loader, pluginInstance);
            pluginInstance.load(context);
        } catch (Throwable e) {
            failedToLoad.put(file, e);
            //logger.error("Failed to load plugin " + fileName + ":\n", e);
        }
    }
}