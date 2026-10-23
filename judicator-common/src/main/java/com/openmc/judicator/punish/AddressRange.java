package com.openmc.judicator.punish;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One IP address ({@code 127.0.0.1}, {@code ::1}) or range in CIDR notation ({@code 10.0.0.0/8}, {@code 2001:db8::/32}).
 * Only literal addresses are accepted, so parsing never triggers a DNS lookup.
 */
public final class AddressRange {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    // Starts with a hex digit or ':' and has a ':': InetAddress then parses it as IPv6 or fails, never asks DNS.
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:][0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

    private final byte[] network;
    private final int prefix;

    private AddressRange(byte[] network, int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    /**
     * @return the range, or empty when the text is not an IP address or CIDR range
     */
    public static Optional<AddressRange> parse(String text) {
        if (text == null) return Optional.empty();
        final String trimmed = text.trim();
        final int slash = trimmed.indexOf('/');
        final Optional<byte[]> address = literal(slash < 0 ? trimmed : trimmed.substring(0, slash));
        if (address.isEmpty()) return Optional.empty();
        final int bits = address.get().length * 8;
        int prefix = bits;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(trimmed.substring(slash + 1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefix < 0 || prefix > bits) return Optional.empty();
        }
        return Optional.of(new AddressRange(address.get(), prefix));
    }

    public boolean contains(String ip) {
        final Optional<byte[]> address = literal(ip);
        if (address.isEmpty() || address.get().length != network.length) return false;
        final byte[] bytes = address.get();
        int remaining = prefix;
        for (int i = 0; i < network.length && remaining > 0; i++, remaining -= 8) {
            final int mask = remaining >= 8 ? 0xFF : (0xFF << (8 - remaining)) & 0xFF;
            if ((bytes[i] & mask) != (network[i] & mask)) return false;
        }
        return true;
    }

    private static Optional<byte[]> literal(String text) {
        if (text == null) return Optional.empty();
        String value = text.trim();
        final int scope = value.indexOf('%');
        if (scope >= 0) value = value.substring(0, scope);
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        if (IPV4.matcher(value).matches()) {
            // An octet above 255 would make InetAddress treat the text as a host name and query DNS.
            for (String octet : value.split("\\.")) {
                if (Integer.parseInt(octet) > 255) return Optional.empty();
            }
        } else if (!IPV6.matcher(value).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value).getAddress());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
