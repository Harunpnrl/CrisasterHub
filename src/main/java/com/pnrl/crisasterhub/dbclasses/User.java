package com.pnrl.crisasterhub.dbclasses;

public class User {

    int user_id;
    String full_name;
    String username;
    String password_hash;
    String email;
    String phone;
    String role;

    public User(int user_id, String full_name, String username, String password_hash, String email, String phone, String role) {
        this.user_id = user_id;
        this.full_name = full_name;
        this.username = username;
        this.password_hash = password_hash;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public User() {
    }

    public int getUser_id() {
        return user_id;
    }
    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }

    public String getFull_name() {
        return full_name;
    }
    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword_hash() {
        return password_hash;
    }
    public void setPassword_hash(String password_hash) {
        this.password_hash = password_hash;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }

}
