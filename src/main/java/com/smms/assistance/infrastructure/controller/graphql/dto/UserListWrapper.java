package com.smms.assistance.infrastructure.controller.graphql.dto;

import java.util.List;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class UserListWrapper {
    public List<UserOutput> users;

    public UserListWrapper() {}

    public UserListWrapper(List<UserOutput> users) {
        this.users = users;
    }
}
