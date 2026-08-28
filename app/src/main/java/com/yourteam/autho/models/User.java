package com.yourteam.autho.models;

public class User {
    private int id;
    private String username;
    private String email;
    private String passwordHash;
    private String Salt;
    private String pinHash;
    private boolean biometricEnabled;
    private String createdAt;
    private String lastLogin;
    private String sessionToken;
    private boolean isAuthenticated;

    public User() {

    }

    public User(String username, String email, String passwordHash, String Salt, String createdAt, String lastLogin, String sessionToken) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.Salt = Salt;
        this.biometricEnabled = false;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.sessionToken = sessionToken;
        this.isAuthenticated = false;
    }
    //getter and setter

    //Id
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    //username
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    //email
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    //passwordHash
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    //salt
    public String getSalt() {
        return Salt;
    }

    public void setSalt(String Salt) {
        this.Salt = Salt;
    }

    //pinhash
    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    //Biometric
    public boolean isBiometricEnabled() {
        return biometricEnabled;
    }

    public void setBiometricEnabled(boolean biometricEnabled) {
        this.biometricEnabled = biometricEnabled;
    }

    //Create At
    public String getCreatedAt(){return createdAt;}
    public void setCreatedAt(String createdAt){this.createdAt = createdAt;}

    //Last Login
    public String getLastLogin(){return lastLogin;}
    public void setLastLogin(String lastLogin){this.lastLogin = lastLogin;}

    //Session Token
    public String getSessionToken(){return sessionToken;}
    public void setSessionToken(String sessionToken){this.sessionToken = sessionToken;}

    //Authentication
    public boolean isAuthenticated(){return isAuthenticated;}
    public void setAuthenticated(boolean isAuthenticated) {this.isAuthenticated = isAuthenticated;}

}

