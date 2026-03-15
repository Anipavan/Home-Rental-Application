package com.spa.home_rental_application.auth_service.auth_service.Entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long role_id;

    @Column(nullable = false, length = 50, unique = true)
    private String roleName;

    @Column(length = 1000)
    private String permissions;

    @ManyToMany(mappedBy = "roles")
    private Set<User> user = new HashSet<>();



    public Role(String roleName) {
        this.roleName = roleName;
    }

    public Role(String roleName, String permissions) {
        this.roleName = roleName;
        this.permissions = permissions;
    }


    public Long getId() { return role_id; }
    public void setId(Long id) { this.role_id = id; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }

    public Set<User> getUsers() { return user; }
    public void setUsers(Set<User> users) { this.user = users; }
}
