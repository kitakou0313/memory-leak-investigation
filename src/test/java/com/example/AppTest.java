package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void greetReturnsCorrectMessage() {
        App app = new App();
        assertEquals("Hello, World!", app.greet("World"));
    }
}
