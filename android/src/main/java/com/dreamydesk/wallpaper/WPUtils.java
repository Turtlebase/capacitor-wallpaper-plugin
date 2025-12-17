package com.dreamydesk.app.plugins;

import android.app.WallpaperManager;
import android.content.Intent;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Wallpaper")
public class WPUtils extends Plugin {

    public void test(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("success", true);
        ret.put("message", "Wallpaper plugin working");
        call.resolve(ret);
    }

    public void setStatic(PluginCall call) {
        String imageUrl = call.getString("imageUrl");
        String screen = call.getString("screen", "BOTH");

        if (imageUrl == null) {
            call.reject("imageUrl is required");
            return;
        }

        try {
            WallpaperManager wallpaperManager =
                    WallpaperManager.getInstance(getContext());

            // TODO: download bitmap and apply wallpaper

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void setLive(PluginCall call) {
        String videoUrl = call.getString("videoUrl");

        if (videoUrl == null) {
            call.reject("videoUrl is required");
            return;
        }

        try {
            Intent intent =
                    new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("requiresUserAction", true);
            call.resolve(ret);

        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
