package com.taylan.darktiles;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final List<Integer> ids = new ArrayList<>();
        for (Field f : R.drawable.class.getFields()) {
            try { ids.add(f.getInt(null)); } catch (Exception ignored) {}
        }
        GridView g = new GridView(this);
        g.setNumColumns(4);
        g.setBackgroundColor(0xFF0E0A1E);
        g.setPadding(24, 48, 24, 24);
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return ids.size(); }
            public Object getItem(int i) { return ids.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageResource(ids.get(i));
                iv.setPadding(12, 12, 12, 12);
                iv.setLayoutParams(new GridView.LayoutParams(-1, 230));
                return iv;
            }
        });
        setContentView(g);
    }
}
