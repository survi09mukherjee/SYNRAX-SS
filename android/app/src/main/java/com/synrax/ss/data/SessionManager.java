package com.synrax.ss.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.synrax.ss.data.model.User;
import java.util.HashSet;
import java.util.Set;

public class SessionManager {
    private static final String PREF_NAME = "SynraxSessionPref";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_RECENT_ROOMS = "recent_rooms";

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor = sharedPreferences.edit();
    }

    /** Save JWT token */
    public void saveAuthToken(String token) {
        editor.putString(KEY_TOKEN, token);
        editor.apply();
    }

    /** Get JWT token */
    public String getAuthToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    /** Save complete user profile details */
    public void saveUser(User user) {
        editor.putInt(KEY_USER_ID, user.getId());
        editor.putString(KEY_USER_EMAIL, user.getEmail());
        editor.putString(KEY_USER_NAME, user.getFullName());
        editor.apply();
    }

    /** Fetch cached user profile */
    public User getUser() {
        int id = sharedPreferences.getInt(KEY_USER_ID, -1);
        String email = sharedPreferences.getString(KEY_USER_EMAIL, null);
        String name = sharedPreferences.getString(KEY_USER_NAME, null);
        
        if (id == -1 || email == null) {
            return null;
        }
        return new User(id, email, name, "");
    }

    /** Check if user is logged in */
    public boolean isLoggedIn() {
        return getAuthToken() != null;
    }

    /** Clear session data (Logout) */
    public void logout() {
        editor.remove(KEY_TOKEN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_USER_NAME);
        editor.apply();
    }

    /** Add event room code to local history list */
    public void addRecentRoom(String roomId) {
        Set<String> rooms = getRecentRooms();
        // Sets in SharedPreferences can't be mutated in place safely, recreate a copy
        Set<String> newRooms = new HashSet<>(rooms);
        newRooms.add(roomId);
        editor.putStringSet(KEY_RECENT_ROOMS, newRooms);
        editor.apply();
    }

    /** Fetch list of recently joined rooms */
    public Set<String> getRecentRooms() {
        return sharedPreferences.getStringSet(KEY_RECENT_ROOMS, new HashSet<>());
    }
}
