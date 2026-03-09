package com.spa.home_rental_application.auth_service.auth_service.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String roleName;

    @Column(length = 1000)
    private String permissions; // JSON string or comma-separated

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    // Constructors
    public Role() {}

    public Role(String roleName) {
        this.roleName = roleName;
    }

    public Role(String roleName, String permissions) {
        this.roleName = roleName;
        this.permissions = permissions;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }

    public Set<User> getUsers() { return users; }
    public void setUsers(Set<User> users) { this.users = users; }
}
