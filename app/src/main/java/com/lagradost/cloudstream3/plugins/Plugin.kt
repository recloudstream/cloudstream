package com.lagradost.cloudstream3.plugins;

import android.content.Context;
import android.content.res.Resources;

public abstract class Plugin {
    public Plugin() {}

    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    public void load(Context context) throws Throwable {}

    public static class Manifest {
        public String name;
        public String pluginClassName;
    }

    public Resources resources;
    public boolean needsResources = false;

    public String __filename;
}