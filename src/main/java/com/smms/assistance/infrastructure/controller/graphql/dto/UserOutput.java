package com.smms.assistance.infrastructure.controller.graphql.dto;

import java.util.UUID;

public class UserOutput {

    public UUID id;
    public String username;
    public String email;
    public String firstName;
    public String lastName;
    public String avatarUrl;
    public UUID roleId;
    public String roleCode;
    public String roleName;
}