package com.zextras.s3browser;

import com.zextras.s3browser.application.usecase.PrefixUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class s3browserApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void parentPrefixWorks() {
        assertEquals("", PrefixUtils.parentPrefix("folder/"));
        assertEquals("folder/", PrefixUtils.parentPrefix("folder/sub/"));
        assertNull(PrefixUtils.parentPrefix(""));
    }
}

