package com.openmc.judicator.punish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressRangeTest {

    private static AddressRange range(String text) {
        return AddressRange.parse(text).orElseThrow(() -> new AssertionError("not parsed: " + text));
    }

    @Test
    void singleAddressMatchesOnlyItself() {
        assertTrue(range("127.0.0.1").contains("127.0.0.1"));
        assertFalse(range("127.0.0.1").contains("127.0.0.2"));
    }

    @Test
    void ipv4Ranges() {
        final AddressRange school = range("10.20.0.0/16");
        assertTrue(school.contains("10.20.0.1"));
        assertTrue(school.contains("10.20.255.254"));
        assertFalse(school.contains("10.21.0.1"));
        assertTrue(range("192.168.1.128/25").contains("192.168.1.200"));
        assertFalse(range("192.168.1.128/25").contains("192.168.1.100"));
        assertTrue(range("0.0.0.0/0").contains("8.8.8.8"));
    }

    @Test
    void ipv6RangesAndAddresses() {
        assertTrue(range("::1").contains("0:0:0:0:0:0:0:1"));
        assertTrue(range("2001:db8::/32").contains("2001:db8:abcd::1"));
        assertFalse(range("2001:db8::/32").contains("2001:db9::1"));
        assertTrue(range("fe80::/10").contains("fe80::1%eth0"), "scope id of a local address is ignored");
    }

    @Test
    void ipv4AndIpv6NeverMatchEachOther() {
        assertFalse(range("0.0.0.0/0").contains("::1"));
        assertFalse(range("::/0").contains("127.0.0.1"));
    }

    @Test
    void anythingThatIsNotALiteralAddressIsRejectedWithoutDnsLookups() {
        assertTrue(AddressRange.parse("localhost").isEmpty());
        assertTrue(AddressRange.parse("example.com").isEmpty());
        assertTrue(AddressRange.parse("cafe").isEmpty(), "hex-looking host names are not addresses");
        assertTrue(AddressRange.parse("10.0.0.0/33").isEmpty());
        assertTrue(AddressRange.parse("10.0.0.0/x").isEmpty());
        assertTrue(AddressRange.parse("999.0.0.1").isEmpty());
        assertTrue(AddressRange.parse("1.2.3.256").isEmpty());
        assertTrue(AddressRange.parse(".1:2").isEmpty());
        assertTrue(AddressRange.parse("1:2:3").isEmpty(), "invalid IPv6 is refused by the parser itself");
        assertTrue(AddressRange.parse("").isEmpty());
        assertTrue(AddressRange.parse(null).isEmpty());
        assertFalse(range("10.0.0.0/8").contains("not-an-ip"));
    }
}
