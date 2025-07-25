package com.openmc.plugin.judicator.punish;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AccessAddress {

    private Long id;
    private String hostAddress;
    private List<String> accounts;
    private LocalDateTime lastUsage;

    public AccessAddress(Long id, String hostAddress, List<String> accounts, LocalDateTime lastUsage) {
        this.id = id;
        this.hostAddress = hostAddress;
        this.accounts = new ArrayList<>(accounts);
        this.lastUsage = lastUsage;
    }

    public static AccessAddress create(String address, String username) {
        return new AccessAddress(null, address, List.of(username.toLowerCase()), LocalDateTime.now());
    }

    public boolean contains(String username) {
        return accounts.contains(username.toLowerCase());
    }

    public void addAccount(String account) {
        this.accounts.add(account.toLowerCase());
    }

    public void removeAccount(String account) {
        this.accounts.remove(account.toLowerCase());
    }


}
