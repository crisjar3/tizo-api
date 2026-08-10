package com.tizo.ecommerce.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrustedClientIpResolver {

    private final List<Network> trustedProxies;

    public TrustedClientIpResolver(
            @Value("${tizo.rate-limit.trusted-proxies:127.0.0.1,::1}") String configuredProxies) {
        this.trustedProxies = Arrays.stream(configuredProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Network::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (trustedProxies.stream().noneMatch(network -> network.contains(remoteAddress))) {
            return remoteAddress;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddress;
        }
        String first = forwarded.split(",", 2)[0].trim();
        return isReasonableAddress(first) ? first : remoteAddress;
    }

    private boolean isReasonableAddress(String value) {
        if (value.length() > 64 || !value.matches("[0-9A-Fa-f:.]{2,64}")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private record Network(byte[] address, int prefixLength) {

        private static Network parse(String value) {
            String[] parts = value.split("/", 2);
            if (!parts[0].matches("[0-9A-Fa-f:.]{2,64}")) {
                throw new IllegalArgumentException("Trusted proxy must be an IP literal or CIDR: " + value);
            }
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int maximum = address.length * Byte.SIZE;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : maximum;
                if (prefix < 0 || prefix > maximum) {
                    throw new IllegalArgumentException("Invalid trusted proxy prefix: " + value);
                }
                return new Network(address, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy: " + value, exception);
            }
        }

        private boolean contains(String candidate) {
            if (candidate == null || !candidate.matches("[0-9A-Fa-f:.]{2,64}")) {
                return false;
            }
            try {
                byte[] other = InetAddress.getByName(candidate).getAddress();
                if (other.length != address.length) {
                    return false;
                }
                int wholeBytes = prefixLength / Byte.SIZE;
                int remainingBits = prefixLength % Byte.SIZE;
                for (int index = 0; index < wholeBytes; index++) {
                    if (address[index] != other[index]) {
                        return false;
                    }
                }
                if (remainingBits == 0) {
                    return true;
                }
                int mask = 0xff << (Byte.SIZE - remainingBits);
                return (address[wholeBytes] & mask) == (other[wholeBytes] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }
}
