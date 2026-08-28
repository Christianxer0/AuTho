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

    private User() {}

    private User(String username, String email, String passwordHash, String Salt) {
        this.username =username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.Salt = Salt;
        this.biometricEnabled = false;
    }
    //getter and setter

    //Id
    public int getId(){return id;}
    public void setId(int id) {this.id = id;}

    //username
    public String getUsername() {return  username;}
    public void setUsername() {this.username = username;}

    //email
    public String getEmail() {return email;}
    public void setEmail(){this.email = email;}

    //passwordHash
    public String getPasswordHash(){return passwordHash;}
    public void setPasswordHash() {this.passwordHash = passwordHash;}

    //salt
    public String getSalt(){return  Salt;}
    public void setSalt(){this.Salt = Salt;}

    //pinhash
    public String getPinHash(){return pinHash;}
    public void setPinHash(){this.pinHash = pinHash;}

    public boolean isBiometricEnabled(){return biometricEnabled;}
    public void setBiometricEnabled(){}

}