package com.yourteam.autho.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.yourteam.autho.models.User;

import java.security.MessageDigest;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


public class UserDatabase extends SQLiteOpenHelper{
    private static final String DATABASE_NAME = "autho.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_PASSWORD_HASH = "password_hash";
    private static final String COLUMN_SALT = "salt";
    private static final String COLUMN_PIN_HASH = "pin_hash";
    private static final String COLUMN_PIN_SALT = "pin_salt";
    private static final String COLUMN_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_LAST_LOGIN = "last_login";
    private static final String COLUMN_SESSION_TOKEN = "session_token";
    private static final String COLUMN_IS_AUTHENTICATED = "is_authenticated";

    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_USERNAME + " TEXT UNIQUE NOT NULL," +
                    COLUMN_EMAIL + " TEXT UNIQUE NOT NULL," +
                    COLUMN_PASSWORD_HASH + " TEXT NOT NULL," +
                    COLUMN_SALT + " TEXT NOT NULL," +
                    COLUMN_PIN_HASH + " TEXT," +
                    COLUMN_PIN_SALT + " TEXT," +
                    COLUMN_BIOMETRIC_ENABLED + " INTEGER DEFAULT 0," +
                    COLUMN_CREATED_AT + " TEXT DEFAULT CURRENT_TIMESTAMP," +
                    COLUMN_LAST_LOGIN + " TEXT," +
                    COLUMN_SESSION_TOKEN + " TEXT," +
                    COLUMN_IS_AUTHENTICATED + " INTEGER DEFAULT 0" +
                    ")";

    public UserDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    //Hash password with salt
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((password + salt).getBytes());
            byte[] hashed = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashed) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    //Generate random salt
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        StringBuilder hexString = new StringBuilder();

        for(byte b: salt){
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    //Register new User
    public boolean registerUser(User user, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);

        values.put(COLUMN_USERNAME, user.getUsername());
        values.put(COLUMN_EMAIL, user.getEmail());
        values.put(COLUMN_PASSWORD_HASH, passwordHash);
        values.put(COLUMN_SALT, salt);
        values.put(COLUMN_BIOMETRIC_ENABLED, user.isBiometricEnabled() ? 1:0);

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result != -1;
    }

    //Login User
    public User loginUser(String usernameOrEmail, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS +
                " WHERE " + COLUMN_USERNAME + " = ? OR " + COLUMN_EMAIL + " = ?";

        Cursor cursor = null;
        try {

            cursor = db.rawQuery(query, new String[]{usernameOrEmail, usernameOrEmail});

            if (cursor.moveToFirst()) {

                int idIdx = cursor.getColumnIndex(COLUMN_ID);
                int userIdx = cursor.getColumnIndex(COLUMN_USERNAME);
                int emailIdx = cursor.getColumnIndex(COLUMN_EMAIL);
                int hashIdx = cursor.getColumnIndex(COLUMN_PASSWORD_HASH);
                int saltIdx = cursor.getColumnIndex(COLUMN_SALT);
                int bioIdx = cursor.getColumnIndex(COLUMN_BIOMETRIC_ENABLED);


                if (hashIdx == -1 || saltIdx == -1 || userIdx == -1 || emailIdx == -1 || idIdx == -1 || bioIdx == -1) {

                    return null;
                }

                String storedHash = cursor.getString(hashIdx);
                String salt = cursor.getString(saltIdx);

                String calculatedHash = hashPassword(password, salt);

                if (calculatedHash != null && calculatedHash.equals(storedHash)) {
                    User user = new User();
                    user.setId(cursor.getInt(idIdx));
                    user.setUsername(cursor.getString(userIdx));
                    user.setEmail(cursor.getString(emailIdx));
                    user.setBiometricEnabled(cursor.getInt(bioIdx) == 1);
                    user.setAuthenticated(true);
                    return user;
                }
            }
            return null;
        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }

    //Set Pin
    public boolean setPin(int userId, String pin){
        SQLiteDatabase db = this.getWritableDatabase();
        String salt = generateSalt();
        String pinHash = hashPassword(pin, salt);

        ContentValues values = new ContentValues();
        values.put(COLUMN_PIN_HASH, pinHash);
        values.put(COLUMN_PIN_SALT, salt);

        int result = db.update(TABLE_USERS, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(userId)});
        db.close();
        return result > 0;
    }

    //Verify Pin
    public boolean verifyPin(int userId, String pin) {
        if (pin == null) return false;  // extra safety

        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_PIN_HASH + ", " + COLUMN_PIN_SALT +
                " FROM " + TABLE_USERS + " WHERE " + COLUMN_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)})) {
            // try‑with‑resources (API 33+) – automatically closes cursor

            if (cursor.moveToFirst()) {
                int hashIdx = cursor.getColumnIndexOrThrow(COLUMN_PIN_HASH);
                int saltIdx = cursor.getColumnIndexOrThrow(COLUMN_PIN_SALT);

                String storedPinHash = cursor.getString(hashIdx);
                String pinSalt = cursor.getString(saltIdx);

                if (storedPinHash == null || pinSalt == null) return false;

                String calculatedPinHash = hashPassword(pin, pinSalt);
                return calculatedPinHash != null && calculatedPinHash.equals(storedPinHash);
            }
            return false;
        }
    }

    //Check if user exist
    public boolean userExists(String username, String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS +
                " WHERE " + COLUMN_USERNAME + " = ? OR " + COLUMN_EMAIL + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{username, email});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    // Update last login
    public void updateLastLogin(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LAST_LOGIN, String.valueOf(System.currentTimeMillis()));
        db.update(TABLE_USERS, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(userId)});
        db.close();
    }

    // Save session token
    public void saveSessionToken(int userId, String token) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_TOKEN, token);
        values.put(COLUMN_IS_AUTHENTICATED, 1);
        db.update(TABLE_USERS, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(userId)});
        db.close();
    }

    // Logout user
    public void logoutUser(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_TOKEN, (String) null);
        values.put(COLUMN_IS_AUTHENTICATED, 0);
        db.update(TABLE_USERS, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(userId)});
        db.close();
    }


}

