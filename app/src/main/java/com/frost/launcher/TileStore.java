package com.frost.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Remembers which apps are on the home row, in which order, and where their baked artwork lives. */
final class TileStore {
    static final class Tile {
        final String id, pkg, label;

        Tile(String id, String pkg, String label) {
            this.id = id;
            this.pkg = pkg;
            this.label = label;
        }
    }

    private final SharedPreferences sp;
    private final File dir;

    TileStore(Context c) {
        sp = c.getSharedPreferences("tiles", Context.MODE_PRIVATE);
        dir = new File(c.getFilesDir(), "tiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    static String idFor(String pkg) {
        return pkg.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    ArrayList<Tile> load() {
        ArrayList<Tile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Tile(o.getString("id"), o.getString("pkg"), o.getString("label")));
            }
        } catch (JSONException ignored) {
            // corrupted list: start empty
        }
        return out;
    }

    void save(List<Tile> list) {
        try {
            JSONArray a = new JSONArray();
            for (Tile t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("pkg", t.pkg);
                o.put("label", t.label);
                a.put(o);
            }
            sp.edit().putString("list", a.toString()).apply();
        } catch (JSONException ignored) {
            // cannot happen with plain strings
        }
    }

    File bg(Tile t) { return new File(dir, t.id + "_bg.png"); }

    File fg(Tile t) { return new File(dir, t.id + "_fg.png"); }

    void deleteFiles(Tile t) {
        //noinspection ResultOfMethodCallIgnored
        bg(t).delete();
        //noinspection ResultOfMethodCallIgnored
        fg(t).delete();
    }
}
