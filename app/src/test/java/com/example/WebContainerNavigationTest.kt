package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContainerNavigationTest {
    private val origin = parseHttpsOrigin("https://app.example.test/")!!

    @Test fun allowsOnlyConfiguredHttpsOriginIncludingSubpaths() {
        assertTrue(isAllowedWebAppUrl("https://app.example.test/login", origin))
        assertTrue(isAllowedWebAppUrl("https://APP.example.test/data", origin))
        assertFalse(isAllowedWebAppUrl("http://app.example.test/", origin))
        assertFalse(isAllowedWebAppUrl("https://evil.example.test/", origin))
        assertFalse(isAllowedWebAppUrl("https://app.example.test.evil.test/", origin))
        assertFalse(isAllowedWebAppUrl("https://user@app.example.test/", origin))
        assertFalse(isAllowedWebAppUrl("file:///etc/passwd", origin))
        assertFalse(isAllowedWebAppUrl("javascript:alert(1)", origin))
        assertFalse(isAllowedWebAppUrl("https://app.example.test:8443/", origin))
    }

    @Test fun invalidOrEmptyConfiguredUrlDisablesWebApp() {
        assertNotNull(parseHttpsOrigin("https://app.example.test"))
        assertFalse(isAllowedWebAppUrl("https://app.example.test/", parseHttpsOrigin("http://app.example.test") ?: origin))
        assertTrue(parseHttpsOrigin("") == null)
        assertTrue(parseHttpsOrigin("http://app.example.test") == null)
        assertTrue(parseHttpsOrigin("https://user@app.example.test") == null)
    }
}
