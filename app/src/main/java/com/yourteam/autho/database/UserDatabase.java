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


}

