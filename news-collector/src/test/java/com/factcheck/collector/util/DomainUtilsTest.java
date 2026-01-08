package com.factcheck.collector.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainUtilsTest {

    @Test
    void normalizeDomainReturnsNullForNullOrBlank() {
        assertThat(DomainUtils.normalizeDomain(null)).isNull();
        assertThat(DomainUtils.normalizeDomain("   ")).isNull();
    }

    @Test
    void normalizeDomainStripsWrappingPunctuation() {
        assertThat(DomainUtils.normalizeDomain("(example.com)")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("\"example.com\"")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("[example.com]")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("{example.com}")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("  (  example.com  )  ")).isEqualTo("example.com");
    }

    @Test
    void normalizeDomainAddsSchemeForBareDomainAndLowercases() {
        assertThat(DomainUtils.normalizeDomain("Example.COM")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("example.com/path?q=1")).isEqualTo("example.com");
    }

    @Test
    void normalizeDomainStripsLeadingWww() {
        assertThat(DomainUtils.normalizeDomain("www.example.com")).isEqualTo("example.com");
        assertThat(DomainUtils.normalizeDomain("https://WWW.Example.com/a")).isEqualTo("example.com");
    }

    @Test
    void normalizeDomainExtractsHostFromUrlWithUserInfoAndPort() {
        assertThat(DomainUtils.normalizeDomain("https://user:pass@Example.com:8443/a/b"))
                .isEqualTo("example.com");
    }

    @Test
    void normalizeDomainConvertsIdnToAsciiPunycode() {
        // Unicode domain; expected output is its ACE (punycode) form.
        assertThat(DomainUtils.normalizeDomain("https://bücher.example/"))
                .isEqualTo("xn--bcher-kva.example");
    }

    @Test
    void normalizeDomainReturnsNullForClearlyInvalidHost() {
        assertThat(DomainUtils.normalizeDomain("not a domain")).isNull();
        assertThat(DomainUtils.normalizeDomain("http://exa mple.com")).isNull();
        assertThat(DomainUtils.normalizeDomain("http://")).isNull();
    }
}