/***
 * Copyright (c) 2012 readyState Software Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.readystatesoftware.simpl3r.utils;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.SharedPreferences;

public class SharedPreferencesUtils {

	public static void setStringArrayPref(SharedPreferences prefs, String key, ArrayList<String> values) {
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray a = new JSONArray();
        for (int i = 0; i < values.size(); i++) {
            a.put(values.get(i));
        }
        if (!values.isEmpty()) {
            editor.putString(key, a.toString());
        } else {
            editor.putString(key, null);
        }
        SharedPreferencesCompat.apply(editor);
    }

    public static ArrayList<String> getStringArrayPref(SharedPreferences prefs, String key) {
        String json = prefs.getString(key, null);
        ArrayList<String> values = new ArrayList<String>();
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String val = a.optString(i);
                    values.add(val);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return values;
    }
    
}
