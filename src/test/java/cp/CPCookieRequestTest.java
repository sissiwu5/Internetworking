package cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CPCookieRequestTest {
    @Test
    @DisplayName("Cookie request message creation test")
    void createTest() {
        CPCookieRequestMsg req = new CPCookieRequestMsg();
        req.create(null);
        assertEquals("cp cookie_request", new String(req.getDataBytes()));
    }
}
